package com.Badnng.moe.recognition

import java.util.Locale

enum class RecognitionTextSource {
    General,
    Sms,
    Notification;

    val supportsCustomBlocking: Boolean
        get() = this == Sms || this == Notification
}

object RecognitionPromptPolicy {
    fun resolve(defaultPrompt: String, customPrompt: String?): String =
        customPrompt?.takeIf { it.isNotBlank() } ?: defaultPrompt

    fun shouldPersist(prompt: String, defaultPrompt: String): Boolean =
        prompt.isNotBlank() && prompt != defaultPrompt
}

object RecognitionPickupLocationPolicy {
    const val SETTINGS_KEY = "custom_pickup_locations"

    fun resolve(text: String, configuredLocations: String, recognizedLocation: String?): String? {
        val normalizedText = text.filterNot(Char::isWhitespace)
        return configuredLocations.split(',')
            .map(String::trim)
            .firstOrNull { keyword ->
                keyword.isNotBlank() && normalizedText.contains(keyword.filterNot(Char::isWhitespace))
            }
            ?: recognizedLocation
    }
}

object RecognitionBlockedWordsPolicy {
    const val MAX_WORDS = 100

    fun normalize(words: Iterable<String>): List<String> {
        val seen = HashSet<String>()
        return buildList {
            for (word in words) {
                val normalized = word.trim()
                if (normalized.isEmpty()) continue
                val comparisonKey = normalized.lowercase(Locale.ROOT)
                if (seen.add(comparisonKey)) add(normalized)
                if (size == MAX_WORDS) break
            }
        }
    }

    fun firstMatch(text: String, words: Iterable<String>): String? =
        normalize(words).firstOrNull { word -> text.contains(word, ignoreCase = true) }
}
