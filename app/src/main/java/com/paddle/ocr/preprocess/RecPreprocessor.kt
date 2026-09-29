// Copyright (c) 2026 PaddlePaddle Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.paddle.ocr.preprocess

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.ceil

data class RecPreprocessResult(
    val tensorData: FloatArray,
    val shape: LongArray,
)

object RecPreprocessor {
    private const val FIXED_HEIGHT = 48
    private const val MAX_IMG_W = 3200

    /** 归一化常数:`(x / 255 - 0.5) / 0.5` 即 `x * (1/127.5) - 1`。 */
    private const val REC_ALPHA = 1.0 / 127.5
    private const val REC_BETA = -1.0

    /**
     * 融合归一化:`(x / 127.5 - 1)` 一次 `convertTo` 完成(旧实现是 divide + subtract 两遍)。
     *
     * 三通道一起处理,因此这里**没有** Scalar,也就顺带消除了旧代码注释里担心的
     * "单值 Scalar 只设 val[0]"隐患。
     *
     * ⚠️ 数学等价但非浮点逐位等价:旧式 `divide` 与 `subtract` 之间舍入过一次 float32,
     * 新式只算一次 `x*alpha + beta`。实测(全 8bit 取值域,以真 OpenCV 核为基准)
     * 最大绝对误差 **1.19e-07**(恰好 1 ULP @|v|≈1),对 CTC 识别无实际影响。
     */
    private fun normalizeRec(mat: Mat) {
        mat.convertTo(mat, CvType.CV_32F, REC_ALPHA, REC_BETA)
    }

    fun preprocessBatch(crops: List<Mat>): RecPreprocessResult {
        // Convert BGR to RGB and resize to fixed height while preserving aspect ratio
        val resizedMats = mutableListOf<Mat>()
        for (crop in crops) {
            // Convert BGR to RGB (model expects RGB input)
            val rgb = Mat()
            Imgproc.cvtColor(crop, rgb, Imgproc.COLOR_BGR2RGB)
            val h = rgb.rows()
            val w = rgb.cols()
            val aspectRatio = if (h > 0) w.toDouble() / h else 1.0
            val newW = ceil(FIXED_HEIGHT * aspectRatio).toInt().coerceAtMost(MAX_IMG_W)
            val dst = Mat()
            Imgproc.resize(rgb, dst, Size(newW.toDouble(), FIXED_HEIGHT.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
            rgb.release()
            resizedMats.add(dst)
        }

        // Convert to float and normalize: (x / 255 - 0.5) / 0.5 = x / 127.5 - 1
        val floatMats = mutableListOf<Mat>()
        for (mat in resizedMats) {
            val floatMat = Mat(mat.rows(), mat.cols(), CvType.CV_32FC3)
            mat.convertTo(floatMat, CvType.CV_32F)
            // (x / 255 - 0.5) / 0.5 = x / 127.5 - 1,融合成一次 convertTo
            normalizeRec(floatMat)

            floatMats.add(floatMat)
            mat.release()  // Release resized mat
        }
        resizedMats.clear()

        val maxW = floatMats.maxOf { it.cols() }
        val n = floatMats.size

        // Pad to max width
        val paddedMats = mutableListOf<Mat>()
        for (mat in floatMats) {
            if (mat.cols() == maxW) {
                paddedMats.add(mat)
            } else {
                val padded = Mat(FIXED_HEIGHT, maxW, CvType.CV_32FC3, org.opencv.core.Scalar(0.0))
                val roi = padded.submat(0, FIXED_HEIGHT, 0, mat.cols())
                mat.copyTo(roi)
                roi.release()
                mat.release()
                paddedMats.add(padded)
            }
        }
        floatMats.clear()

        // Build tensor data
        val channelSize = FIXED_HEIGHT * maxW
        val tensorData = FloatArray(n * 3 * channelSize)
        for (b in 0 until n) {
            val mat = paddedMats[b]
            val channels = mutableListOf<Mat>()
            Core.split(mat, channels)
            for (c in 0..2) {
                val buf = FloatArray(channelSize)
                channels[c].get(0, 0, buf)
                System.arraycopy(buf, 0, tensorData, (b * 3 + c) * channelSize, channelSize)
                channels[c].release()
            }
            mat.release()
        }
        paddedMats.clear()

        return RecPreprocessResult(
            tensorData = tensorData,
            shape = longArrayOf(n.toLong(), 3, FIXED_HEIGHT.toLong(), maxW.toLong()),
        )
    }

    /**
     * NPU(QNN HTP)路径专用:输出尺寸必须严格等于模型被固化的 `[batch, 3, targetH, targetW]`。
     *
     * 处理方式与动态路径保持一致(保持长宽比缩放到目标高 → 右侧补 0),
     * 区别只是宽度被钉死。样本数不足 [batch] 时重复最后一张补齐,
     * **调用方必须按真实样本数截断返回结果**(见 RecognitionEngine)。
     *
     * ⚠️ **宽度不够会丢字**:若文本行的宽高比超过 `targetW / targetH`,
     * 预处理只能把它**横向压扁**,CTC 的固定时间步(T = targetW/8)表达不了那么多字符,
     * 表现为重复字/密集字符被吃掉。实测(PP-OCRv6 tiny rec,高 48):
     *
     * | 固化宽度 | 阈值(宽高比) | 可容纳 | 实测正确率 |
     * |---|---|---|---|
     * | 320 | 6.67 | 约 9 个中文字 | **3/6** |
     * | 640 | 13.33 | 约 20 字 | 5/6 |
     * | 1280 | 26.67 | 约 42 字 | 5/6 |
     *
     * 例:`这是一段用于测试长文本行压缩影响的中文句子内容`(宽高比 20.6)
     * 在 320 下被识别为 `…句子內容`(错字),在 640 及以上正确。
     *
     * 因此模型固化宽度应按**业务里最长的文本行**来选,见
     * `tools/qnn/convert_to_npu.py --rec-size`。
     */
    fun preprocessBatchFixed(
        crops: List<Mat>,
        targetH: Int,
        targetW: Int,
        batch: Int,
    ): RecPreprocessResult {
        require(crops.isNotEmpty()) { "crops must not be empty" }
        val effectiveBatch = batch.coerceAtLeast(1)
        val slotCount = minOf(crops.size, effectiveBatch)
        val channelSize = targetH * targetW
        val tensorData = FloatArray(effectiveBatch * 3 * channelSize)

        for (b in 0 until effectiveBatch) {
            val crop = crops[if (b < slotCount) b else slotCount - 1]
            fillFixedSlot(crop, targetH, targetW, tensorData, b * 3 * channelSize, channelSize)
        }

        return RecPreprocessResult(
            tensorData = tensorData,
            shape = longArrayOf(effectiveBatch.toLong(), 3, targetH.toLong(), targetW.toLong()),
        )
    }

    private fun fillFixedSlot(
        crop: Mat,
        targetH: Int,
        targetW: Int,
        out: FloatArray,
        base: Int,
        channelSize: Int,
    ) {
        val rgb = Mat()
        Imgproc.cvtColor(crop, rgb, Imgproc.COLOR_BGR2RGB)
        val h = rgb.rows()
        val w = rgb.cols()
        val aspectRatio = if (h > 0) w.toDouble() / h else 1.0
        val newW = ceil(targetH * aspectRatio).toInt().coerceIn(1, targetW)

        val resized = Mat()
        Imgproc.resize(rgb, resized, Size(newW.toDouble(), targetH.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
        rgb.release()

        val floatCrop = Mat(targetH, newW, CvType.CV_32FC3)
        resized.convertTo(floatCrop, CvType.CV_32F)
        resized.release()
        // (x / 255 - 0.5) / 0.5 = x / 127.5 - 1,融合成一次 convertTo
        normalizeRec(floatCrop)

        // 右侧补 0:与动态路径一致,补的是归一化之后的 0
        val padded = Mat(targetH, targetW, CvType.CV_32FC3, Scalar(0.0))
        if (newW < targetW) {
            val roi = padded.submat(0, targetH, 0, newW)
            floatCrop.copyTo(roi)
            roi.release()
        } else {
            floatCrop.copyTo(padded)
        }
        floatCrop.release()

        val channels = mutableListOf<Mat>()
        Core.split(padded, channels)
        for (c in 0..2) {
            val buf = FloatArray(channelSize)
            channels[c].get(0, 0, buf)
            System.arraycopy(buf, 0, out, base + c * channelSize, channelSize)
            channels[c].release()
        }
        padded.release()
    }
}
