package com.Badnng.moe.ocr

internal object RecognitionTextStructure {
    fun normalizeForCodeMatching(
        text: String,
        datetimePattern: String,
        spaceCollapsePattern: String,
        charRemovals: List<String>,
        corrections: List<Pair<String, String>>,
    ): String {
        var result = text
            .replace(Regex(datetimePattern), "")
            .replace(Regex(spaceCollapsePattern), "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        for (removal in charRemovals) {
            result = result.replace(removal, "")
        }
        for ((from, to) in corrections) {
            result = result.replace(from, to)
        }
        return result
    }

    fun trailingLocationCandidates(
        lines: List<String>,
        labels: List<String>,
    ): List<String> {
        if (lines.isEmpty() || labels.isEmpty()) return emptyList()
        val normalizedLines = lines.map { it.replace("\n", "").trim() }
        val candidates = mutableListOf<String>()

        normalizedLines.forEachIndexed { index, line ->
            for (label in labels) {
                if (label.isBlank()) continue
                val labelIndex = line.lastIndexOf(label)
                if (labelIndex < 0) continue
                val sameLine = line.substring(0, labelIndex).trim(' ', ':', '：', '>', '<')
                val candidate = sameLine.ifBlank {
                    normalizedLines.getOrNull(index - 1)
                        ?.trim(' ', ':', '：', '>', '<')
                        .orEmpty()
                }
                if (candidate.isNotBlank()) candidates += candidate
            }
        }
        return candidates.distinct()
    }
}
