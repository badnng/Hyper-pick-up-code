package com.Badnng.moe.recognition

import android.content.Context
import org.json.JSONArray

object RecognitionBlockedWordsPreferences {
    const val PREF_KEY = "recognition_blocked_words"

    fun load(context: Context): List<String> {
        val encoded = settings(context).getString(PREF_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(encoded)
            RecognitionBlockedWordsPolicy.normalize(
                buildList {
                    for (index in 0 until array.length()) {
                        array.optString(index).takeIf { it.isNotEmpty() }?.let(::add)
                    }
                },
            )
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, words: Iterable<String>): List<String> {
        val normalized = RecognitionBlockedWordsPolicy.normalize(words)
        settings(context).edit().apply {
            if (normalized.isEmpty()) {
                remove(PREF_KEY)
            } else {
                putString(PREF_KEY, JSONArray(normalized).toString())
            }
        }.apply()
        return normalized
    }

    fun firstMatch(context: Context, text: String): String? =
        RecognitionBlockedWordsPolicy.firstMatch(text, load(context))

    private fun settings(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
}
