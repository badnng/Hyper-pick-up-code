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

package com.paddle.ocr.postprocess

import android.graphics.PointF
import com.paddle.ocr.model.OCRBox
import com.paddle.ocr.util.MathUtils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

object DBPostProcessor {
    private const val MIN_SIZE_BEFORE_UNCLIP = 3f
    private const val MIN_SIZE_AFTER_UNCLIP = 5f

    fun process(
        pred: FloatArray,
        predShape: LongArray,
        thresh: Float,
        boxThresh: Float,
        unclipRatio: Float,
        maxCandidates: Int,
        useDilation: Boolean,
        scoreMode: String,
        boxType: String,
        originalH: Int,
        originalW: Int,
        /**
         * 概率图 → 原图 的显式仿射映射 `x_orig = (x_model - offsetX) * scaleX`。
         *
         * - `scaleX <= 0`(默认):退化为 `原图宽 / 概率图宽` 的线性缩放,offset 为 0
         *   —— 这是改造前的行为,适用于"squash 整图"与"原生分辨率分块"两条路径。
         * - letterbox(NPU 默认路径):必须显式传入,因为补边偏移无法从尺寸反推。
         *   只按尺寸比例缩放会忽略 padX/padY,导致框整体偏移。
         */
        scaleX: Double = 0.0,
        scaleY: Double = 0.0,
        offsetX: Double = 0.0,
        offsetY: Double = 0.0,
    ): List<OCRBox> {
        require(boxType == "quad") { "Only DBPostProcess box_type=quad is supported" }

        val pH = predShape[2].toInt()
        val pW = predShape[3].toInt()
        // 显式映射优先;未给出时退回按尺寸比缩放(offset 视为 0)
        val sx = if (scaleX > 0.0) scaleX else originalW.toDouble() / pW
        val sy = if (scaleY > 0.0) scaleY else originalH.toDouble() / pH
        val ox = offsetX
        val oy = offsetY
        val normalizedScoreMode = scoreMode.lowercase()

        val rawProb = Mat(pH, pW, CvType.CV_32FC1)
        val mask = Mat(pH, pW, CvType.CV_8UC1)
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        var contourMask = mask

        return try {
            rawProb.put(0, 0, pred)
            val threshMat = Mat()
            try {
                Imgproc.threshold(rawProb, threshMat, thresh.toDouble(), 255.0, Imgproc.THRESH_BINARY)
                threshMat.convertTo(mask, CvType.CV_8UC1)
            } finally {
                threshMat.release()
            }

            contourMask = if (useDilation) {
                val kernel = Mat.ones(2, 2, CvType.CV_8UC1)
                try {
                    Mat().also { Imgproc.dilate(mask, it, kernel) }
                } finally {
                    kernel.release()
                }
            } else {
                mask
            }

            Imgproc.findContours(contourMask, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            val boxes = mutableListOf<OCRBox>()
            val numContours = minOf(contours.size, maxCandidates)
            for (index in 0 until numContours) {
                val contour = contours[index]
                val contour2f = MatOfPoint2f(*contour.toArray())
                val rect = try {
                    Imgproc.minAreaRect(contour2f)
                } finally {
                    contour2f.release()
                }
                val sside = minOf(rect.size.width, rect.size.height)
                if (sside < MIN_SIZE_BEFORE_UNCLIP) continue

                val rectPoints = Array(4) { Point() }
                rect.points(rectPoints)
                val orderedPoints = QuadGeometry.orderMinAreaRectPoints(rectPoints)
                val score = if (normalizedScoreMode == "slow") {
                    computeBoxScore(rawProb, contour.toList())
                } else {
                    computeBoxScore(rawProb, orderedPoints)
                }
                if (score < boxThresh) continue

                val expandedPts = PolygonUnclip.unclip(orderedPoints, unclipRatio)
                val expandedRect = MatOfPoint2f().let { expanded2f ->
                    try {
                        expanded2f.fromList(expandedPts)
                        Imgproc.minAreaRect(expanded2f)
                    } finally {
                        expanded2f.release()
                    }
                }
                val sside2 = minOf(expandedRect.size.width, expandedRect.size.height)
                if (sside2 < MIN_SIZE_AFTER_UNCLIP) continue

                val expandedBox = Array(4) { Point() }
                expandedRect.points(expandedBox)
                val ePts = QuadGeometry.orderMinAreaRectPoints(expandedBox)
                val scaled = listOf(
                    scalePoint(ePts[0], sx, sy, ox, oy, originalW, originalH),
                    scalePoint(ePts[1], sx, sy, ox, oy, originalW, originalH),
                    scalePoint(ePts[2], sx, sy, ox, oy, originalW, originalH),
                    scalePoint(ePts[3], sx, sy, ox, oy, originalW, originalH),
                )

                val boxW = kotlin.math.hypot(
                    scaled[1].x - scaled[0].x,
                    scaled[1].y - scaled[0].y,
                )
                val boxH = kotlin.math.hypot(
                    scaled[3].x - scaled[0].x,
                    scaled[3].y - scaled[0].y,
                )
                if (boxW <= 3 || boxH <= 3) continue

                boxes.add(OCRBox(points = scaled))
            }
            boxes
        } finally {
            hierarchy.release()
            if (contourMask !== mask) {
                contourMask.release()
            }
            contours.forEach { it.release() }
            mask.release()
            rawProb.release()
        }
    }

    /** 概率图坐标 → 原图坐标:`(p - offset) * scale`,再钳到图像范围内。 */
    private fun scalePoint(
        point: Point,
        scaleX: Double,
        scaleY: Double,
        offsetX: Double,
        offsetY: Double,
        originalW: Int,
        originalH: Int,
    ): PointF {
        return PointF(
            MathUtils.roundHalfToEven((point.x - offsetX) * scaleX)
                .coerceIn(0, originalW).toFloat(),
            MathUtils.roundHalfToEven((point.y - offsetY) * scaleY)
                .coerceIn(0, originalH).toFloat(),
        )
    }

    private fun computeBoxScore(probMap: Mat, points: List<Point>): Float {
        if (points.isEmpty()) return 0f

        val h = probMap.rows()
        val w = probMap.cols()
        val xmin = points.minOf { kotlin.math.floor(it.x).toInt() }.coerceIn(0, w - 1)
        val xmax = points.maxOf { kotlin.math.ceil(it.x).toInt() }.coerceIn(0, w - 1)
        val ymin = points.minOf { kotlin.math.floor(it.y).toInt() }.coerceIn(0, h - 1)
        val ymax = points.maxOf { kotlin.math.ceil(it.y).toInt() }.coerceIn(0, h - 1)

        val mask = Mat(ymax - ymin + 1, xmax - xmin + 1, CvType.CV_8UC1, Scalar(0.0))
        val pts = MatOfPoint().apply {
            fromList(
                points.map { Point((it.x - xmin).toInt().toDouble(), (it.y - ymin).toInt().toDouble()) }
            )
        }
        Imgproc.fillPoly(mask, mutableListOf(pts), Scalar(1.0))

        val roi = probMap.submat(ymin, ymax + 1, xmin, xmax + 1)
        val mean = Core.mean(roi, mask)
        roi.release()
        mask.release()
        pts.release()
        return mean.`val`[0].toFloat()
    }
}
