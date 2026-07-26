package com.Badnng.moe.helper

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import java.util.Locale

data class ImageSourceMetadata(
    val displayName: String?,
    val packageName: String?,
    val appName: String?,
)

object ImageSourceMetadataResolver {
    fun resolve(context: Context, uri: Uri): ImageSourceMetadata {
        val displayName = queryDisplayName(context, uri)
        val packageName = displayName?.let(ScreenshotFileNameParser::extractPackageName)
        val appName = packageName?.let { resolveApplicationLabel(context, it) }
        return ImageSourceMetadata(
            displayName = displayName,
            packageName = packageName,
            appName = appName,
        )
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        val providerName = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(displayNameIndex)
                } else {
                    null
                }
            }
        }.getOrNull()

        return providerName
            ?.takeIf { it.isNotBlank() }
            ?: Uri.decode(uri.lastPathSegment.orEmpty())
                .substringAfterLast('/')
                .takeIf { it.isNotBlank() }
    }

    private fun resolveApplicationLabel(context: Context, packageName: String): String? =
        runCatching {
            val applicationInfo = context.packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0),
            )
            context.packageManager.getApplicationLabel(applicationInfo)
                .toString()
                .takeIf { it.isNotBlank() }
        }.getOrNull()
}

internal object ScreenshotFileNameParser {
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "avif")
    private val trailingEditSuffix = Regex(
        pattern = "(?i)(?:[_-](?:edit(?:ed)?|crop(?:ped)?|copy|longshot|\\d+))+$",
    )
    private val packageName = Regex(
        pattern = "(?i)(?:^|[_-])((?:[a-z][a-z0-9_]*\\.)+[a-z][a-z0-9_]*)",
    )

    fun extractPackageName(fileName: String): String? {
        val simpleName = fileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
        if (simpleName.isEmpty()) return null

        val extension = simpleName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val stem = if (extension in imageExtensions) {
            simpleName.dropLast(extension.length + 1)
        } else {
            simpleName
        }
        val normalizedStem = trailingEditSuffix.replace(stem, "")
        val isScreenshot = normalizedStem.contains("screenshot", ignoreCase = true) ||
            normalizedStem.startsWith("截屏") ||
            normalizedStem.startsWith("屏幕截图")
        if (!isScreenshot) return null

        return packageName.findAll(normalizedStem)
            .lastOrNull()
            ?.groupValues
            ?.getOrNull(1)
    }
}
