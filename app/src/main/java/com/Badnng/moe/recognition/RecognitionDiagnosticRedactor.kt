package com.Badnng.moe.recognition

object RecognitionDiagnosticRedactor {
    private const val REDACTED = "[REDACTED]"
    private val bearerPattern = Regex(
        pattern = "(?i)(bearer\\s+)[a-z0-9._~+/=-]{8,}",
    )
    private val namedSecretPattern = Regex(
        pattern = "(?i)([\"']?(?:api[_-]?key|access[_-]?token|authorization)[\"']?\\s*[:=]\\s*[\"']?)([^\"',\\s}]{4,})",
    )
    private val commonApiKeyPattern = Regex(
        pattern = "(?i)\\bsk-[a-z0-9_-]{8,}\\b",
    )

    fun redact(text: String?, knownSecrets: Collection<String> = emptyList()): String? {
        if (text == null) return null
        var result: String = text
        knownSecrets.asSequence()
            .map { secret -> secret.trim() }
            .filter { secret -> secret.isNotEmpty() }
            .distinct()
            .sortedByDescending { secret -> secret.length }
            .forEach { secret -> result = result.replace(secret, REDACTED) }
        result = bearerPattern.replace(result) { match -> match.groupValues[1] + REDACTED }
        result = namedSecretPattern.replace(result) { match -> match.groupValues[1] + REDACTED }
        result = commonApiKeyPattern.replace(result, REDACTED)
        return result
    }
}
