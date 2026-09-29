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

package com.paddle.ocr.engine

import android.graphics.Bitmap
import com.paddle.ocr.NpuOptions
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.model.OCRBox
import com.paddle.ocr.postprocess.DBPostProcessor
import com.paddle.ocr.preprocess.DetPreprocessResult
import com.paddle.ocr.preprocess.DetPreprocessor
import org.opencv.core.Mat

class DetectionEngine(
    private val ortManager: ORTSessionManager,
    private val config: PaddleOCRConfig,
    /** NPU 相关参数;仅当后端为 NPU 时其字段才会被用到。 */
    private val npu: NpuOptions = NpuOptions(),
) {
    data class DetectionResult(
        val boxes: List<OCRBox>,
        val preprocessMs: Long,
        val inferenceMs: Long,
        val postprocessMs: Long,
        val timeMs: Long,
        val inputShape: List<Int>,
        /** 本次实际跑的分块数;整图路径恒为 1。 */
        val tileCount: Int = 1,
    )

    fun detect(bitmap: Bitmap): DetectionResult {
        // Bitmap 路径:Mat 由本方法创建,也由本方法释放
        val mat = DetPreprocessor.toBgrMat(bitmap)
        return try {
            run(mat)
        } finally {
            mat.release()
        }
    }

    /** [src] 的生命周期由**调用方**负责,本方法不释放它。 */
    fun detect(src: Mat): DetectionResult = run(src)

    /**
     * 检测入口。三种输入策略:
     *
     * 1. **CPU / NNAPI**:模型是动态 shape,按配置的 limit 策略整体缩放(改造前行为);
     * 2. **NPU + 分块**(默认):固定 shape 仅用作**画布**。在原图原生分辨率上重叠切块,
     *    块内不缩放,概率图按像素 max 合并。字高零损失,精度可达/超过 CPU 路径;
     * 3. **NPU + 整图**:整图缩放进画布。图像长宽比接近画布时才适用,否则长边被严重压缩。
     */
    private fun run(src: Mat): DetectionResult {
        val fixed = ortManager.detFixedInputShape
        if (fixed == null || fixed.size != 4) {
            // CPU / NNAPI:模型动态 shape,按配置的 limit 策略整体缩放(改造前行为)
            return detectWholeImage(src, useFixed = false)
        }
        if (npu.detTiledInference) {
            return detectTiled(src, fixed)
        }
        // 画布宽高比与图像差太多时,letterbox 会留下大片补边、有效分辨率被浪费
        // (例如把 4:3 的图塞进 0.45 的竖屏画布)。此时自动改用分块,避免精度塌陷。
        if (npu.aspectGuardTolerance > 0 && aspectMismatch(src, fixed) > npu.aspectGuardTolerance) {
            return detectTiled(src, fixed)
        }
        return detectWholeImage(src, useFixed = true)
    }

    /** 画布宽高比与图像宽高比的相对偏差(0 表示完全一致)。 */
    private fun aspectMismatch(src: Mat, fixed: LongArray): Double {
        val imgAspect = src.cols().toDouble() / src.rows().toDouble()
        val canvasAspect = fixed[3].toDouble() / fixed[2].toDouble()
        return kotlin.math.abs(imgAspect - canvasAspect) / canvasAspect
    }

    // ------------------------------------------------------------------ 分块路径

    private fun detectTiled(src: Mat, fixed: LongArray): DetectionResult {
        val h = src.rows()
        val w = src.cols()
        // 块尺寸**必须**等于模型固化的输入 W×H —— 固定 shape 的会话只接受这一个尺寸。
        val tileH = fixed[2].toInt()
        val tileW = fixed[3].toInt()
        val plan = DetPreprocessor.tilePlan(
            h, w, tileW, tileH, npu.detTileOverlap, npu.maxProbPixels,
        )

        var preprocessMs = 0L
        var inferenceMs = 0L
        val accumulated = FloatArray(plan.accSize)
        for (i in 0 until plan.tileCount) {
            val p0 = System.currentTimeMillis()
            val tile = DetPreprocessor.preprocessTile(src, plan, i, config.detImgMode)
            preprocessMs += System.currentTimeMillis() - p0

            val i0 = System.currentTimeMillis()
            val (prob, _) = ortManager.runDetection(tile.tensorData, tile.shape)
            inferenceMs += System.currentTimeMillis() - i0

            DetPreprocessor.mergeTile(accumulated, prob, plan, i)
        }

        return finishDetection(
            probData = accumulated,
            probH = plan.accH,
            probW = plan.accW,
            originalH = h,
            originalW = w,
            preprocessMs = preprocessMs,
            inferenceMs = inferenceMs,
            tileCount = plan.tileCount,
            modelShape = longArrayOf(1, 3, tileH.toLong(), tileW.toLong()),
        )
    }

    // ------------------------------------------------------------------ 整图路径

    private fun detectWholeImage(src: Mat, useFixed: Boolean): DetectionResult {
        val preStart = System.currentTimeMillis()
        val fixed = ortManager.detFixedInputShape
        val pre = if (useFixed && fixed != null && fixed.size == 4) {
            DetPreprocessor.preprocessFixed(src, fixed[3].toInt(), fixed[2].toInt(), config.detImgMode)
        } else {
            DetPreprocessor.preprocess(
                src,
                config.detLimitSideLen,
                config.detLimitType,
                config.detMaxSideLimit,
                config.detImgMode,
            )
        }
        val preprocessMs = System.currentTimeMillis() - preStart

        val infStart = System.currentTimeMillis()
        val (outputData, outputShape) = ortManager.runDetection(pre.tensorData, pre.shape)
        val inferenceMs = System.currentTimeMillis() - infStart

        return finishDetection(
            probData = outputData,
            probH = outputShape[2].toInt(),
            probW = outputShape[3].toInt(),
            originalH = pre.originalH,
            originalW = pre.originalW,
            preprocessMs = preprocessMs,
            inferenceMs = inferenceMs,
            tileCount = 1,
            modelShape = outputShape,
            // letterbox 的补边偏移无法从尺寸反推,必须显式传给后处理
            scaleX = pre.scaleX,
            scaleY = pre.scaleY,
            offsetX = pre.offsetX,
            offsetY = pre.offsetY,
        )
    }

    // ------------------------------------------------------------------ 后处理

    /**
     * 概率图落成检测框。
     *
     * [probData] 的尺寸可能与原图不同:
     * - 分块路径:整图坐标系(受 [NpuOptions.maxProbPixels] 限制可能降采样);
     * - 整图路径:模型输出尺寸,由 letterbox 的 scale/offset 还原。
     *
     * 未显式给出映射时(scaleX <= 0),DBPostProcessor 退回
     * `原图宽 / 概率图宽` 的线性缩放 —— 与改造前行为一致。
     */
    private fun finishDetection(
        probData: FloatArray,
        probH: Int,
        probW: Int,
        originalH: Int,
        originalW: Int,
        preprocessMs: Long,
        inferenceMs: Long,
        tileCount: Int,
        modelShape: LongArray,
        scaleX: Double = 0.0,
        scaleY: Double = 0.0,
        offsetX: Double = 0.0,
        offsetY: Double = 0.0,
    ): DetectionResult {
        val postStart = System.currentTimeMillis()
        val boxes = DBPostProcessor.process(
            pred = probData,
            predShape = longArrayOf(1, 1, probH.toLong(), probW.toLong()),
            thresh = config.detThresh,
            boxThresh = config.detBoxThresh,
            unclipRatio = config.detUnclipRatio,
            maxCandidates = config.detMaxCandidates,
            useDilation = config.detUseDilation,
            scoreMode = config.detScoreMode,
            boxType = config.detBoxType,
            originalH = originalH,
            originalW = originalW,
            scaleX = scaleX,
            scaleY = scaleY,
            offsetX = offsetX,
            offsetY = offsetY,
        )
        val postprocessMs = System.currentTimeMillis() - postStart

        return DetectionResult(
            boxes = boxes,
            preprocessMs = preprocessMs,
            inferenceMs = inferenceMs,
            postprocessMs = postprocessMs,
            timeMs = preprocessMs + inferenceMs + postprocessMs,
            inputShape = modelShape.map { it.toInt() },
            tileCount = tileCount,
        )
    }
}
