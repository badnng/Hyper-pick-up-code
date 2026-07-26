package com.Badnng.moe.helper

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.Badnng.moe.data.db.OrderDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object ScreenshotStorage {
    const val DIRECTORY_NAME = "澎湃记识别截图"
    val relativePath: String = "${Environment.DIRECTORY_DOWNLOADS}/$DIRECTORY_NAME/"

    data class Stats(
        val size: Long = 0L,
        val fileCount: Int = 0,
    )

    data class MigrationResult(
        val migratedFiles: Int = 0,
        val updatedOrders: Int = 0,
        val updatedGroups: Int = 0,
        val failedFiles: Int = 0,
    )

    fun saveBitmap(
        context: Context,
        bitmap: Bitmap,
        namePrefix: String = "识别截图",
    ): String = createEntry(
        context = context,
        displayName = uniqueDisplayName(namePrefix, "png"),
        mimeType = "image/png",
    ) { output ->
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "截图编码失败" }
    }

    fun saveStream(
        context: Context,
        input: InputStream,
        namePrefix: String,
        extension: String,
        mimeType: String = mimeTypeForExtension(extension),
    ): String = createEntry(
        context = context,
        displayName = uniqueDisplayName(namePrefix, normalizeExtension(extension)),
        mimeType = mimeType,
    ) { output ->
        input.copyTo(output)
    }

    fun exists(context: Context, location: String): Boolean {
        if (location.isBlank()) return false
        return runCatching {
            val uri = locationUri(location)
            if (uri != null) {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
            } else {
                File(location).isFile
            }
        }.getOrDefault(false)
    }

    fun imageModel(location: String): Any {
        val uri = locationUri(location)
        return uri ?: File(location)
    }

    fun openInputStream(context: Context, location: String): InputStream? {
        if (location.isBlank()) return null
        val uri = locationUri(location)
        return if (uri != null) {
            context.contentResolver.openInputStream(uri)
        } else {
            File(location).takeIf(File::isFile)?.let(::FileInputStream)
        }
    }

    fun decodeBounds(context: Context, location: String): Pair<Int, Int>? = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openInputStream(context, location)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
        if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth to options.outHeight
        } else {
            null
        }
    }.getOrNull()

    fun extension(context: Context, location: String): String {
        val displayName = queryDisplayName(context, location)
        val source = displayName ?: location.substringBefore('?').substringAfterLast('/')
        return normalizeExtension(source.substringAfterLast('.', "img"))
    }

    fun delete(context: Context, location: String): Boolean {
        if (location.isBlank()) return true
        return runCatching {
            val uri = locationUri(location)
            if (uri != null) {
                context.contentResolver.delete(uri, null, null) > 0 || !exists(context, location)
            } else {
                val file = File(location)
                !file.exists() || file.delete()
            }
        }.getOrDefault(false)
    }

    fun readStats(context: Context): Stats {
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.MediaColumns.SIZE)
        return runCatching {
            resolver.query(
                downloadsCollection(),
                projection,
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(relativePath),
                null,
            )?.use { cursor ->
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                var size = 0L
                var count = 0
                while (cursor.moveToNext()) {
                    size += cursor.getLong(sizeIndex).coerceAtLeast(0L)
                    count++
                }
                Stats(size, count)
            } ?: Stats()
        }.getOrDefault(Stats())
    }

    fun deleteAll(context: Context): Boolean {
        val resolver = context.contentResolver
        return runCatching {
            resolver.delete(
                downloadsCollection(),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(relativePath),
            )
            true
        }.getOrDefault(false)
    }

    suspend fun migrateLegacyScreenshots(context: Context): MigrationResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val legacyRoot = File(appContext.filesDir, "screenshots")
        if (!legacyRoot.isDirectory) return@withContext MigrationResult()

        val legacyFiles = legacyRoot.walkTopDown().filter(File::isFile).toList()
        if (legacyFiles.isEmpty()) return@withContext MigrationResult()

        val migratedLocations = linkedMapOf<String, String>()
        val createdLocations = mutableListOf<String>()
        var failedFiles = 0
        legacyFiles.forEach { file ->
            runCatching {
                val extension = normalizeExtension(file.extension)
                val location = file.inputStream().use { input ->
                    saveStream(
                        context = appContext,
                        input = input,
                        namePrefix = "历史_${file.nameWithoutExtension}",
                        extension = extension,
                    )
                }
                migratedLocations[file.canonicalPath] = location
                createdLocations += location
            }.onFailure {
                failedFiles++
                AppLogger.update("Screenshot migration copy failed: ${file.absolutePath}, ${it.message}")
            }
        }

        if (migratedLocations.isEmpty()) {
            return@withContext MigrationResult(failedFiles = failedFiles)
        }

        val database = OrderDatabase.getDatabase(appContext)
        val orderDao = database.orderDao()
        val groupDao = database.orderGroupDao()
        var updatedOrders = 0
        var updatedGroups = 0
        try {
            database.withTransaction {
                orderDao.getAllOrdersList().forEach { order ->
                    migratedLocation(order.screenshotPath, migratedLocations)?.let { newLocation ->
                        orderDao.update(order.copy(screenshotPath = newLocation))
                        updatedOrders++
                    }
                }
                groupDao.getAllGroupsList().forEach { group ->
                    migratedLocation(group.screenshotPath, migratedLocations)?.let { newLocation ->
                        groupDao.updateGroup(group.copy(screenshotPath = newLocation))
                        updatedGroups++
                    }
                }
            }
        } catch (error: Exception) {
            createdLocations.forEach { delete(appContext, it) }
            throw error
        }

        migratedLocations.keys.forEach { path -> runCatching { File(path).delete() } }
        legacyRoot.walkBottomUp()
            .filter(File::isDirectory)
            .forEach { directory -> if (directory.listFiles().isNullOrEmpty()) directory.delete() }

        MigrationResult(
            migratedFiles = migratedLocations.size,
            updatedOrders = updatedOrders,
            updatedGroups = updatedGroups,
            failedFiles = failedFiles,
        )
    }

    private fun createEntry(
        context: Context,
        displayName: String,
        mimeType: String,
        write: (OutputStream) -> Unit,
    ): String {
        val resolver = context.applicationContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = checkNotNull(resolver.insert(downloadsCollection(), values)) {
            "无法在下载目录创建截图"
        }
        try {
            resolver.openOutputStream(uri, "w")?.use(write)
                ?: error("无法写入截图")
            check(
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                ) > 0,
            ) { "无法完成截图写入" }
            return uri.toString()
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    private fun queryDisplayName(context: Context, location: String): String? {
        val uri = locationUri(location) ?: return File(location).name
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) null
                else cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }.getOrNull()
    }

    private fun migratedLocation(
        location: String,
        migratedLocations: Map<String, String>,
    ): String? {
        if (location.isBlank() || locationUri(location) != null) return null
        return runCatching { migratedLocations[File(location).canonicalPath] }.getOrNull()
    }

    private fun locationUri(location: String): Uri? {
        val parsed = Uri.parse(location)
        return parsed.takeIf { it.scheme == "content" || it.scheme == "file" }
    }

    private fun uniqueDisplayName(prefix: String, extension: String): String {
        val safePrefix = prefix
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), "_")
            .trim('.', ' ', '_')
            .take(80)
            .ifBlank { "识别截图" }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(Date())
        val suffix = UUID.randomUUID().toString().take(6)
        return "${safePrefix}_${timestamp}_$suffix.${normalizeExtension(extension)}"
    }

    private fun normalizeExtension(extension: String): String =
        extension.lowercase(Locale.ROOT).takeIf { it in setOf("png", "jpg", "jpeg", "webp") } ?: "png"

    private fun mimeTypeForExtension(extension: String): String = when (normalizeExtension(extension)) {
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "image/png"
    }

    private fun downloadsCollection(): Uri =
        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
}
