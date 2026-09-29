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

import android.graphics.Bitmap
import com.paddle.ocr.util.BitmapUtils
import com.paddle.ocr.util.ImageUtils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

data class DetPreprocessResult(
    val tensorData: FloatArray,
    val shape: LongArray,
    val originalH: Int,
    val originalW: Int,
    /**
     * 概率图 → 原图 的仿射映射:`x_orig = (x_model - offsetX) * scaleX`。
     *
     * letterbox 路径必须显式给出(因为存在补边偏移);其余路径保持 0,
     * 由 DBPostProcessor 按 `原图宽 / 概率图宽` 推导 —— 与改造前行为一致。
     */
    val scaleX: Double = 0.0,
    val scaleY: Double = 0.0,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    /** 分块推理时的块序号;非分块路径为 -1。 */
    val tileIndex: Int = -1,
    val tileX: Int = 0,
    val tileY: Int = 0,
    val tileW: Int = 0,
    val tileH: Int = 0,
) {
    val isTile: Boolean get() = tileIndex >= 0
}

object DetPreprocessor {
    private val mean = doubleArrayOf(0.485, 0.456, 0.406)
    private val std = doubleArrayOf(0.229, 0.224, 0.225)
    private const val scale = 1.0 / 255.0

    fun preprocess(
        bitmap: Bitmap,
        limitSideLen: Int,
        limitType: String,
        maxSideLimit: Int,
        imgMode: String,
    ): DetPreprocessResult {
        val src = BitmapUtils.bitmapToBGRMat(bitmap)
        return try {
            preprocess(src, limitSideLen, limitType, maxSideLimit, imgMode)
        } finally {
            src.release()
        }
    }

    fun preprocess(
        src: Mat,
        limitSideLen: Int,
        limitType: String,
        maxSideLimit: Int,
        imgMode: String,
    ): DetPreprocessResult {
        val originalH = src.rows()
        val originalW = src.cols()
        val input = if (imgMode.uppercase() == "RGB") {
            Mat().also { Imgproc.cvtColor(src, it, Imgproc.COLOR_BGR2RGB) }
        } else {
            src
        }

        val resized = ImageUtils.resizeToMultipleOf32(input, limitSideLen, limitType, maxSideLimit)
        if (input !== src) input.release()

        val h = resized.rows()
        val w = resized.cols()
        // 动态路径没有补边:平面步长恰好等于 h*w、偏移为 0,归一化结果**就是**最终张量。
        // 直接按平面写进去,省掉旧实现的"每通道新建 FloatArray 再逐块 arraycopy"两轮中转。
        val tensorData = FloatArray(3 * h * w)
        val floatMat = Mat(h, w, CvType.CV_32FC3)
        resized.convertTo(floatMat, CvType.CV_32F)
        resized.release()
        normalizeFloatIntoPlane(floatMat, tensorData, h * w, 0, w)
        floatMat.release()

        return DetPreprocessResult(
            tensorData = tensorData,
            shape = longArrayOf(1, 3, h.toLong(), w.toLong()),
            originalH = originalH,
            originalW = originalW,
        )
    }

    fun preprocessFixed(
        bitmap: Bitmap,
        targetW: Int,
        targetH: Int,
        imgMode: String,
    ): DetPreprocessResult {
        val src = BitmapUtils.bitmapToBGRMat(bitmap)
        return try {
            preprocessFixed(src, targetW, targetH, imgMode)
        } finally {
            src.release()
        }
    }

    /**
     * NPU(QNN HTP)路径的**默认**预处理:保持长宽比缩放 + 居中补边(letterbox),
     * 把整图放进模型固化的 [targetW] × [targetH] 画布,**单次推理**。
     *
     * ## 为什么必须 letterbox 而不是直接拉伸
     *
     * 早期版本做的是各向异性 squash(直接 resize 到 targetW × targetH)。
     * 当画布是正方形、而手机截屏是 1200×2670 时,横向 0.80×、纵向 0.36×,
     * **横纵差 2.2 倍**,文字被压扁,检测框落不到字上。
     *
     * letterbox 保证横纵等比(`r` 相同),因此不产生形变。
     * 前提是**画布形状要接近图像形状**(见 `NpuOptions.aspectGuardTolerance`):
     * 竖屏截屏用竖屏画布(如 1216×2688),就没有补边浪费,也无需下采样。
     *
     * 补边值取归一化空间的 0(等价于均值色),对网络最中性。
     *
     * 坐标还原:前向是 `x_model = padX + x_orig * r`,
     * 因此逆映射为 `x_orig = (x_model - padX) / r`,通过 [DetPreprocessResult.scaleX]
     * 与 [DetPreprocessResult.offsetX] 交给 DBPostProcessor 执行。
     */
    fun preprocessFixed(
        src: Mat,
        targetW: Int,
        targetH: Int,
        imgMode: String,
    ): DetPreprocessResult {
        val originalH = src.rows()
        val originalW = src.cols()
        require(originalW > 0 && originalH > 0) { "empty image" }
        val input = if (imgMode.uppercase() == "RGB") {
            Mat().also { Imgproc.cvtColor(src, it, Imgproc.COLOR_BGR2RGB) }
        } else {
            src
        }

        val r = minOf(targetW.toDouble() / originalW, targetH.toDouble() / originalH)
        val contentW = maxOf(1, Math.round(originalW * r).toInt())
        val contentH = maxOf(1, Math.round(originalH * r).toInt())
        val padX = (targetW - contentW) / 2
        val padY = (targetH - contentH) / 2

        val resized = Mat()
        Imgproc.resize(
            input,
            resized,
            Size(contentW.toDouble(), contentH.toDouble()),
            0.0,
            0.0,
            Imgproc.INTER_LINEAR,
        )
        if (input !== src) input.release()

        // 归一化结果**直接写进画布的目标位置**,不再先产出一整块 contentH×contentW×3 的中间
        // 数组再逐行搬进来 —— 那一轮 39MB 的分配 + 39MB 的拷贝是纯浪费。
        val targetSize = targetH * targetW
        val tensorData = FloatArray(3 * targetSize)
        val floatResized = Mat(contentH, contentW, CvType.CV_32FC3)
        resized.convertTo(floatResized, CvType.CV_32F)
        resized.release()
        normalizeFloatIntoPlane(
            floatResized,
            tensorData,
            targetSize,
            padY * targetW + padX,
            targetW,
        )
        floatResized.release()

        return DetPreprocessResult(
            tensorData = tensorData,
            shape = longArrayOf(1, 3, targetH.toLong(), targetW.toLong()),
            originalH = originalH,
            originalW = originalW,
            scaleX = 1.0 / r,
            scaleY = 1.0 / r,
            offsetX = padX.toDouble(),
            offsetY = padY.toDouble(),
        )
    }

    /**
     * NPU 路径的**默认**预处理:原生分辨率分块推理。
     *
     * 背景:QNN HTP 不支持动态 shape,det 模型被固化成 [1,3,S,S]。若把整张图压进这个
     * 正方形画布,长边会被严重压缩(手机截屏 1200×2670 → 960×960,纵向 0.36x),
     * 小字直接消失,检测框落不到文字上。
     *
     * 做法:按 [tileSize] × [tileSize]、以 [overlap] 比例重叠,在原图**原生分辨率**上
     * 逐块推理(块内不做任何缩放),再按像素取 max 合并概率图。这样字高完全不损失,
     * 检测精度可以达到甚至超过 CPU 路径。
     *
     * 返回的 [DetPreprocessResult.shape] 是**整图**尺寸而非画布尺寸,
     * 因此 DBPostProcessor 的 scaleX/scaleY 恒为 1,坐标可直接使用。
     * [tilePlan] 携带分块几何信息,供 [mergeTiles] 把各块概率图写回整图坐标系。
     */
    fun tilePlan(
        originalH: Int,
        originalW: Int,
        tileW: Int,
        tileH: Int,
        overlap: Double,
        maxProbPixels: Int,
    ): TilePlan {
        // 重叠比例钳到 [0, 0.5]:超过 0.5 会让步长过小,分块数平方级增长而收益递减
        val safeOverlap = overlap.coerceIn(0.0, 0.5)
        val strideX = maxOf(1, (tileW * (1.0 - safeOverlap)).toInt())
        val strideY = maxOf(1, (tileH * (1.0 - safeOverlap)).toInt())
        // 概率图累计缓冲按**像素总数**设上限:长截图(如 1080×20000)全分辨率累计要 86MB,
        // 按 1/f² 降采样后只需几 MB。DB 后处理会用 `原图宽/特征图宽` 把框放大回原图,
        // 坐标依然正确 —— 这与 CPU 路径受 detMaxSideLimit 限制时的行为一致。
        // 常规截屏(1200×2670 = 320 万像素)远低于上限,factor = 1,零损失。
        val pixels = originalH.toLong() * originalW.toLong()
        val factor = if (maxProbPixels <= 0 || pixels <= maxProbPixels) {
            1
        } else {
            maxOf(1, kotlin.math.ceil(kotlin.math.sqrt(pixels.toDouble() / maxProbPixels)).toInt())
        }
        return TilePlan(
            originalH = originalH,
            originalW = originalW,
            tileW = tileW,
            tileH = tileH,
            strideX = strideX,
            strideY = strideY,
            accFactor = factor,
        )
    }

    /**
     * 分块几何。块尺寸**必须等于模型固化的输入 W×H**(可以是矩形),
     * 否则喂给会话的 shape 对不上,会直接抛异常。
     *
     * 切块位置用**居中收边**:最后一块贴住右/下边界,
     * 避免边缘出现不足一块的碎条(碎条会被补边,浪费算力且可能漏检)。
     *
     * 所有尺寸都从**实际图像尺寸**推导,不依赖任何屏幕分辨率常量 ——
     * 换一台分辨率不同的设备,只是分块数变了,坐标换算与精度不受影响。
     */
    data class TilePlan(
        val originalH: Int,
        val originalW: Int,
        /** 块宽 = 模型输入宽。 */
        val tileW: Int,
        /** 块高 = 模型输入高。 */
        val tileH: Int,
        val strideX: Int,
        val strideY: Int,
        /** 概率图累计缓冲相对原图的降采样倍数,>= 1。 */
        val accFactor: Int = 1,
    ) {
        val ys: IntArray = axisStarts(originalH, tileH, strideY)
        val xs: IntArray = axisStarts(originalW, tileW, strideX)
        val tileCount: Int get() = ys.size * xs.size

        /** 累计缓冲尺寸(已降采样)。 */
        val accH: Int get() = maxOf(1, originalH / accFactor)
        val accW: Int get() = maxOf(1, originalW / accFactor)
        val accSize: Int get() = accH * accW

        private companion object {
            fun axisStarts(length: Int, tile: Int, stride: Int): IntArray {
                if (length <= tile) return intArrayOf(0)
                val starts = ArrayList<Int>()
                var s = 0
                while (s + tile < length) {
                    starts.add(s)
                    s += stride
                }
                starts.add(length - tile)
                return starts.toIntArray()
            }
        }
    }

    /**
     * 把原图按 [plan] 切出第 [index] 块,归一化成模型输入张量。
     *
     * - 块尺寸恰好等于 `tileW × tileH` 时**不做任何缩放**(字高零损失);
     * - 边缘不足一块时,用归一化空间的中性值 0 补齐到块尺寸
     *   (0 对应均值色,对网络最中性,不会在补边处产生虚假响应)。
     */
    fun preprocessTile(
        src: Mat,
        plan: TilePlan,
        index: Int,
        imgMode: String,
    ): DetPreprocessResult {
        val tw = plan.tileW
        val th = plan.tileH
        val yi = index / plan.xs.size
        val xi = index % plan.xs.size
        val y0 = plan.ys[yi]
        val x0 = plan.xs[xi]
        val roiH = minOf(th, plan.originalH - y0)
        val roiW = minOf(tw, plan.originalW - x0)

        val input = if (imgMode.uppercase() == "RGB") {
            Mat().also { Imgproc.cvtColor(src, it, Imgproc.COLOR_BGR2RGB) }
        } else {
            src
        }

        val planeSize = tw * th
        val tensorData = FloatArray(3 * planeSize)
        // 块内归一化后直接写进块画布:块不足一整块时右侧/下侧从未被写过,
        // 保持初始 0 = 归一化空间的中性补边值。
        val roi = input.submat(y0, y0 + roiH, x0, x0 + roiW)
        val normRoi = Mat(roiH, roiW, CvType.CV_32FC3)
        roi.convertTo(normRoi, CvType.CV_32F)
        roi.release()
        normalizeFloatIntoPlane(normRoi, tensorData, planeSize, 0, tw)
        normRoi.release()
        if (input !== src) input.release()

        return DetPreprocessResult(
            tensorData = tensorData,
            shape = longArrayOf(1, 3, th.toLong(), tw.toLong()),
            originalH = plan.originalH,
            originalW = plan.originalW,
            tileIndex = index,
            tileX = x0,
            tileY = y0,
            tileW = roiW,
            tileH = roiH,
        )
    }

    /**
     * 把第 [index] 块的模型输出(`tileW × tileH` 概率图)按像素 max 写回整图累计缓冲。
     *
     * 用 max 而非平均:重叠区只要有一块检出了就算检出,等价于"取最自信的响应",
     * 与 PaddleOCR 官方切图推理的做法一致。
     *
     * 累计缓冲可能相对原图降采样了 [TilePlan.accFactor] 倍(长截图省内存);
     * 写回时按该倍数折算坐标。DB 后处理拿到的是降采样后的 shape,
     * 会用 `原图宽/特征图宽` 把框自动放大回原图坐标,无需额外处理。
     */
    fun mergeTile(
        accumulated: FloatArray,
        prob: FloatArray,
        plan: TilePlan,
        index: Int,
    ) {
        val tw = plan.tileW
        val th = plan.tileH
        val yi = index / plan.xs.size
        val xi = index % plan.xs.size
        val y0 = plan.ys[yi]
        val x0 = plan.xs[xi]
        val roiH = minOf(th, plan.originalH - y0)
        val roiW = minOf(tw, plan.originalW - x0)
        val f = plan.accFactor
        val accW = plan.accW
        for (r in 0 until roiH) {
            val ay = (y0 + r) / f
            if (ay >= plan.accH) continue
            val srcBase = r * tw
            val dstBase = ay * accW
            for (c in 0 until roiW) {
                val ax = (x0 + c) / f
                if (ax >= accW) continue
                val v = prob[srcBase + c]
                val d = dstBase + ax
                if (v > accumulated[d]) accumulated[d] = v
            }
        }
    }

    /** Bitmap → BGR Mat;调用方负责 release。 */
    fun toBgrMat(bitmap: Bitmap): Mat = BitmapUtils.bitmapToBGRMat(bitmap)

    /**
     * 单通道归一化:`(x * scale - mean[c]) / std[c]`,融合成**一次**仿射变换
     * `x * (scale / std[c]) + (-mean[c] / std[c])`。
     *
     * 旧实现是 multiply → subtract → divide 三段全图扫描;三个通道就是 9 遍。
     * 融合后每通道只扫 1 遍(共 3 遍),这是 det 预处理在 CPU / NPU 两条路径上
     * 共有的第二大热点。
     *
     * ⚠️ **数学等价但非浮点逐位等价**:旧式在三段之间各舍入一次 float32
     * (`multiply` → `subtract` → `divide`),新式只算一次 `x*alpha + beta`,末位必然可能不同。
     * 实测(全 8bit 取值域,以真 OpenCV 核为基准)三通道最大绝对误差 **4.77e-07**
     * (约 2~3 ULP @|v|≈2.2);1200×2670 实测图每通道仅 1/3204000 个像素不同。
     * 相对量级 1e-7,远低于 8bit 量化步长(≈1/255),对 OCR 无实际影响。
     *
     * 另注:OpenCV 对 CV_32F→CV_32F 的 `convertTo` 内部走 `cvt_32f`(工作类型 float,
     * `dst[j] = src[j]*a + b`,SIMD 路径用 FMA),且 alpha/beta 会由 double 收窄为 float
     * (modules/core/src/convert_scale.simd.hpp:93,280,82)。
     *
     * `convertTo` 就地(src === dst)是安全的:dispatch 里先 `Mat src = *this` 再
     * `dst.create(...)`(同尺寸同类型为 no-op),不会覆写源数据
     * (modules/core/src/convert.dispatch.cpp:280-300)。输入必须是 `CV_32FC1`(Core.split 的结果)。
     */
    private fun normalizeChannel(channel: Mat, c: Int) {
        val alpha = scale / std[c]
        val beta = -mean[c] / std[c]
        channel.convertTo(channel, CvType.CV_32F, alpha, beta)
    }

    /**
     * 把已经是 CV_32FC3 的 [floatMat] 归一化后按 CHW **直接写进** [dst] 的指定平面位置,
     * 不产生任何整块中间数组。det 的三条路径([preprocess] 动态整图、[preprocessFixed]
     * letterbox 画布、[preprocessTile] 分块)全部走这里,保证行为一致。
     *
     * 旧实现是"先归一化出一整块 h×w×3 的数组,再逐行 arraycopy 进画布",
     * 每次 det 调用(含每次 Pass2 裁剪区)都要白付一次约 39MB 的分配 + 39MB 的拷贝;
     * 而那块数组的唯一用途就是被搬走,搬完立刻变垃圾。
     *
     * 写入布局与旧实现逐行 arraycopy 完全一致:
     * `dst[c * planeStride + rowDstOffset + r * rowStride + x] = norm(x, r, 通道 c)`。
     *
     * @param planeStride 通道平面步长(CHW 下为 `targetH * targetW`)。
     * @param rowDstOffset 平面内首行起始偏移,即 `padY * targetW + padX`;整图路径为 0。
     * @param rowStride 目标中一行的跨度,即目标宽度;等于内容宽度时各行首尾相接。
     *
     * **补边保持 0 的原理**:补边像素的索引不落在任何一次 `System.arraycopy` 的目标区间内,
     * 而 [dst] 是 `FloatArray`,JVM 保证初始化为全 0 —— 0 正是归一化空间的中性值
     * (等价于均值色)。这里既不清零、也不做任何越界或外扩写入。
     *
     * 传输代价:每通道 **1 次** `Mat.get`(整通道)+ 一次整块或逐行的纯 JVM `arraycopy`,
     * 全程只有 3 次 JNI 取数,且不产生任何整块中转数组。
     * `rowStride == w` 时(整图动态路径、整宽块)走**一次整块** `System.arraycopy` 快路径。
     */
    private fun normalizeFloatIntoPlane(
        floatMat: Mat,
        dst: FloatArray,
        planeStride: Int,
        rowDstOffset: Int,
        rowStride: Int,
    ) {
        val h = floatMat.rows()
        val w = floatMat.cols()
        val channels = mutableListOf<Mat>()
        Core.split(floatMat, channels)

        // 每通道只发 **一次** `Mat.get` 把整个通道读出来(每次 get 都是一次 JNI 往返,
        // 逐行 get 会变成 h 次 JNI —— 2688 行 × 3 通道 = 8064 次,开销远超省下的拷贝)。
        // 缓冲按通道元素数精确分配并在 3 个通道间复用,每次都整段覆盖,不残留上一通道数据。
        val planeSize = h * w
        val planeBuf = FloatArray(planeSize)
        for (c in 0..2) {
            val chan = channels[c]
            normalizeChannel(chan, c)
            chan.get(0, 0, planeBuf)
            val planeBase = c * planeStride + rowDstOffset
            if (rowStride == w) {
                // 目标行跨度 == 内容宽度(如整图动态路径、整宽的块),
                // 内容在目标平面里连续 → 一次整块拷贝,不做逐行。
                System.arraycopy(planeBuf, 0, dst, planeBase, planeSize)
            } else {
                // 有补边:逐行搬(纯 JVM 侧 arraycopy,无 JNI)。两侧各行目标地址
                // 首尾相接,补边像素从不被写入。
                for (r in 0 until h) {
                    System.arraycopy(planeBuf, r * w, dst, planeBase + r * rowStride, w)
                }
            }
            chan.release()
        }
    }
}
