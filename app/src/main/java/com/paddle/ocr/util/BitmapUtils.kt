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

package com.paddle.ocr.util

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

object BitmapUtils {

    fun imdecodeBGR(imageBytes: ByteArray): Mat {
        val encoded = MatOfByte(*imageBytes)
        return try {
            Imgcodecs.imdecode(encoded, Imgcodecs.IMREAD_COLOR)
        } finally {
            encoded.release()
        }
    }

    fun bitmapToBGRMat(bitmap: Bitmap): Mat {
        return bitmapToMat(bitmap, Imgproc.COLOR_RGBA2BGR)
    }

    fun bitmapToRGBMat(bitmap: Bitmap): Mat {
        return bitmapToMat(bitmap, Imgproc.COLOR_RGBA2RGB)
    }

    fun bgrMatToBitmap(mat: Mat): Bitmap {
        val rgba = Mat()
        return try {
            Imgproc.cvtColor(mat, rgba, Imgproc.COLOR_BGR2RGBA)
            Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888).also { bitmap ->
                Utils.matToBitmap(rgba, bitmap)
            }
        } finally {
            rgba.release()
        }
    }

    private fun bitmapToMat(bitmap: Bitmap, colorConversionCode: Int): Mat {
        // 位图本来就是 ARGB_8888 时直接用原图,不再 copy()。
        // 原代码无条件 copy() 出一份 ARGB_8888,唯一用途就是喂给下面 Utils.bitmapToMat
        // ——而它自己就会把像素拷进 rgba Mat,copy() 那份随即被回收,是纯浪费:
        // 1200×2670 全图一次 = 12.8MB 无谓拷贝,Pass2 的每个裁剪区还要各付一次。
        //
        // 注意 Bitmap.Config.HARDWARE(硬件位图)不能直接读像素(getPixels 会抛),
        // 它的 config 也不等于 ARGB_8888,因此天然仍走 copy() 路径 ——
        // 不要把这里改成「跳过所有 copy」。
        val bmp = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        // 只有自己 copy 出来的那份才归本方法所有,才能回收。
        // 跳过 copy 时 bmp === bitmap,那是**调用方**的位图(调用方之后还会用它保存截图等),
        // 一旦在这里 recycle() 会让调用方后续使用直接崩溃 —— 必须用 owned 守卫。
        val owned = bmp !== bitmap
        val rgba = Mat(bmp.height, bmp.width, CvType.CV_8UC4)
        val dst = Mat()
        return try {
            Utils.bitmapToMat(bmp, rgba)
            Imgproc.cvtColor(rgba, dst, colorConversionCode)
            dst
        } catch (t: Throwable) {
            dst.release()
            throw t
        } finally {
            if (owned) bmp.recycle()
            rgba.release()
        }
    }
}
