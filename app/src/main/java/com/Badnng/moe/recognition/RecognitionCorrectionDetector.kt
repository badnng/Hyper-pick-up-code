package com.Badnng.moe.recognition

/**
 * 从原始识别文本中找出带有明确取件语义，或位于快递上下文中的独立分段码，
 * 但尚未被规则产出的码值。这里只用于判断是否需要进入纠正识别，不参与正式订单识别。
 */
object RecognitionCorrectionDetector {
    private val labelledCodePattern = Regex(
        pattern = "(?:取件码|取餐码|取茶码|取货码|取餐号|取件号|取单口令|取件口令|取餐口令)\\s*[:：]?\\s*([^\\s，,。；;]{1,40})",
        option = RegexOption.IGNORE_CASE,
    )
    private val actionCodePattern = Regex(
        pattern = "(?:使用|凭|报)\\s*([A-Za-z0-9][A-Za-z0-9.#_-]{0,39})\\s*(?:取件|取餐|取货)",
        option = RegexOption.IGNORE_CASE,
    )
    private val standaloneSegmentedCodePattern = Regex(
        pattern = "(?m)^\\s*([0-9]{1,3}-[0-9]{1,3}-[0-9]{3,6})\\s*$",
    )
    private val expressContextPattern = Regex(
        pattern = "快递|取件|取货|驿站|菜鸟|邮政|申通|中通|圆通|韵达|极兔|顺丰|京东物流",
        option = RegexOption.IGNORE_CASE,
    )

    fun findUnrecognizedCodes(
        fullText: String,
        recognizedCodes: Collection<String>,
    ): List<String> {
        if (fullText.isBlank()) return emptyList()
        val recognized = recognizedCodes.mapTo(mutableSetOf(), ::normalizeCode)
        val hasExpressContext = expressContextPattern.containsMatchIn(fullText)
        return sequence {
            yieldAll(labelledCodePattern.findAll(fullText).map { it.groupValues[1] })
            yieldAll(actionCodePattern.findAll(fullText).map { it.groupValues[1] })
            if (hasExpressContext) {
                // 部分快递列表只在首项展示品牌，后续取件码会独占一行且没有“取件码”前缀。
                // 这里只将其加入待纠正候选，不直接创建订单。
                yieldAll(standaloneSegmentedCodePattern.findAll(fullText).map { it.groupValues[1] })
            }
        }
            .map(::cleanCandidate)
            .filter(::looksLikePickupCode)
            .distinctBy(::normalizeCode)
            .filterNot { normalizeCode(it) in recognized }
            .toList()
    }

    private fun cleanCandidate(value: String): String = value
        .trim()
        .trim(',', '，', ':', '：', '.', '。', ';', '；')

    private fun looksLikePickupCode(value: String): Boolean {
        if (value.isBlank() || value.length > 40) return false
        if (value.any(Char::isDigit)) return true
        return value.length <= 8 && value.all { it.isLetter() && it.code < 128 }
    }

    private fun normalizeCode(value: String): String = value
        .trim()
        .filterNot(Char::isWhitespace)
        .trim(',', '，', ':', '：', '.', '。', ';', '；')
        .lowercase()
}