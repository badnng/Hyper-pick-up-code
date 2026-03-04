package com.Badnng.moe

import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

data class LogEntry(
    val level: String,
    val tag: String,
    val message: String,
    val time: String
)

object LogManager {
    val logs = mutableStateListOf<LogEntry>()
    private var job: Job? = null

    fun startCollecting() {
        if (job != null) return
        logs.clear()
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                Runtime.getRuntime().exec("logcat -c").waitFor()
                val process = Runtime.getRuntime().exec("logcat -v time")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    parseLine(line!!)?.let { logs.add(it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun parseLine(line: String): LogEntry? {
        return try {
            val time = line.substring(0, 18)
            val levelChar = line.substring(19, 20)
            val rest = line.substring(21)
            val tagEnd = rest.indexOf('(')
            val tag = if (tagEnd != -1) rest.substring(0, tagEnd).trim() else "Unknown"
            val messageStart = rest.indexOf("): ")
            val message = if (messageStart != -1) rest.substring(messageStart + 3) else rest
            
            val level = when (levelChar) {
                "D" -> "DEBUG"
                "I" -> "INFO"
                "W" -> "WARN"
                "E" -> "ERROR"
                else -> "VERBOSE"
            }
            LogEntry(level, tag, message, time)
        } catch (e: Exception) {
            null
        }
    }
}
