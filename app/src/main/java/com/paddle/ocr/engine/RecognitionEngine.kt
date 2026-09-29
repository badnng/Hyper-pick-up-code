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

import com.paddle.ocr.postprocess.CTCDecoder
import com.paddle.ocr.preprocess.RecPreprocessor
import org.opencv.core.Mat

class RecognitionEngine(
    private val ortManager: ORTSessionManager,
    private val characterList: List<String>,
) {
    data class RecognitionResult(
        val texts: List<Pair<String, Float>>,
        val preprocessMs: Long,
        val inferenceMs: Long,
        val postprocessMs: Long,
        val timeMs: Long,
        val inputShape: List<Int>,
    )

    /**
     * NPU 生效时返回模型固化的 batch;否则返回 1。
     * 调用方(OCREngine)据此决定每批塞多少张图。
     */
    fun fixedBatchSize(): Int {
        val fixed = ortManager.recFixedInputShape ?: return 1
        val batch = fixed.getOrNull(0) ?: return 1
        return if (batch > 0) batch.toInt() else 1
    }

    fun recognize(crops: List<Mat>): RecognitionResult {
        if (crops.isEmpty()) {
            return RecognitionResult(emptyList(), 0, 0, 0, 0, emptyList())
        }

        // Preprocess
        val preStart = System.currentTimeMillis()
        val fixed = ortManager.recFixedInputShape
        val preResult = if (fixed != null && fixed.size == 4) {
            RecPreprocessor.preprocessBatchFixed(
                crops = crops,
                targetH = fixed[2].toInt(),
                targetW = fixed[3].toInt(),
                batch = fixed[0].toInt(),
            )
        } else {
            RecPreprocessor.preprocessBatch(crops)
        }
        val preprocessMs = System.currentTimeMillis() - preStart

        // Inference
        val infStart = System.currentTimeMillis()
        val (outputData, outputShape) = ortManager.runRecognition(preResult.tensorData, preResult.shape)
        val inferenceMs = System.currentTimeMillis() - infStart

        // Postprocess (CTC decode)
        val postStart = System.currentTimeMillis()
        val decoded = CTCDecoder.decode(outputData, outputShape, characterList)
        // 固定 batch 不足时会用最后一张图补齐,这里按真实样本数截断
        val texts = if (decoded.size > crops.size) decoded.subList(0, crops.size) else decoded
        val postprocessMs = System.currentTimeMillis() - postStart

        val inputShape = preResult.shape.map { it.toInt() }
        val timeMs = preprocessMs + inferenceMs + postprocessMs
        return RecognitionResult(
            texts = texts,
            preprocessMs = preprocessMs,
            inferenceMs = inferenceMs,
            postprocessMs = postprocessMs,
            timeMs = timeMs,
            inputShape = inputShape,
        )
    }
}
