package com.Badnng.moe.helper

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.BaseColumns
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
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
    private const val LEGACY_INTERNAL_DIRECTORY_NAME = "screenshots"
    private const val NO_MEDIA_FILE_NAME = ".nomedia"
    private const val PENDING_SUFFIX = ".pending"
    private val publicPicturesRelativePath = "${Environment.DIRECTORY_PICTURES}/$DIRECTORY_NAME/"
    private val legacyDownloadsRelativePath = "${Environment.DIRECTORY_DOWNLOADS}/$DIRECTORY_NAME/"

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

    private data class MediaEntry(
        val uri: Uri,
        val displayName: String,
        val size: Long,
    )

    fun saveBitmap(
        context: Context,
        bitmap: Bitmap,
        namePrefix: String = "识别截图",
    ): String = createEntry(
        context = context,
        displayName = uniqueDisplayName(namePrefix, "png"),
    ) { output ->
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "截图编码失败" }
    }

    fun saveStream(
        context: Context,
        input: InputStream,
        namePrefix: String,
        extension: String,
    ): String = createEntry(
        context = context,
        displayName = uniqueDisplayName(namePrefix, normalizeExtension(extension)),
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

    fun mimeType(context: Context, location: String): String = mimeTypeForExtension(
        extension(context, location),
    )

    fun shareUri(context: Context, location: String): Uri? {
        if (location.isBlank()) return null
        val parsed = locationUri(location)
        if (parsed?.scheme == "content") return parsed
        val file = when (parsed?.scheme) {
            "file" -> parsed.path?.let(::File)
            else -> File(location)
        }?.takeIf(File::isFile) ?: return null
        return runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file,
            )
        }.getOrNull()
    }

    fun delete(context: Context, location: String): Boolean {
        if (location.isBlank()) return true
        return runCatching {
            val uri = locationUri(location)
            when (uri?.scheme) {
                "content" ->
                    context.contentResolver.delete(uri, null, null) > 0 || !exists(context, location)
                "file" -> uri.path?.let(::File)?.let { !it.exists() || it.delete() } ?: true
                else -> File(location).let { !it.exists() || it.delete() }
            }
        }.getOrDefault(false)
    }

    fun readStats(context: Context): Stats {
        val appContext = context.applicationContext
        val directories = listOf(
            storageDirectory(appContext),
            legacyInternalDirectory(appContext),
        ).distinctBy { canonicalPathOrAbsolute(it) }
        val directoryStats = directories.map { readDirectoryStats(it) }
        val resolver = appContext.contentResolver
        val publicPictures = readCollectionStats(
            resolver,
            imagesCollection(),
            publicPicturesRelativePath,
        )
        val legacyDownloads = readCollectionStats(
            resolver,
            downloadsCollection(),
            legacyDownloadsRelativePath,
        )
        return Stats(
            size = directoryStats.sumOf { it.size } + publicPictures.size + legacyDownloads.size,
            fileCount = directoryStats.sumOf { it.fileCount } +
                publicPictures.fileCount + legacyDownloads.fileCount,
        )
    }

    fun deleteAll(context: Context): Boolean {
        val appContext = context.applicationContext
        val directoryResults = listOf(
            storageDirectory(appContext),
            legacyInternalDirectory(appContext),
        ).distinctBy { canonicalPathOrAbsolute(it) }.map { clearDirectory(it) }
        val resolver = appContext.contentResolver
        val publicPicturesDeleted = deleteCollection(
            resolver,
            imagesCollection(),
            publicPicturesRelativePath,
        )
        val legacyDownloadsDeleted = deleteCollection(
            resolver,
            downloadsCollection(),
            legacyDownloadsRelativePath,
        )
        return directoryResults.all { it } && publicPicturesDeleted && legacyDownloadsDeleted
    }

    suspend fun migrateLegacyScreenshots(context: Context): MigrationResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val targetDirectory = prepareStorageDirectory(appContext)
        cleanupPendingFiles(targetDirectory)

        val migratedLocations = linkedMapOf<String, String>()
        val createdLocations = mutableListOf<String>()
        val legacyFilesToDelete = mutableListOf<File>()
        val publicLocationsToDelete = mutableListOf<String>()
        val existingByDisplayName = targetDirectory.listFiles()
            ?.filter {
                it.isFile &&
                    it.name != NO_MEDIA_FILE_NAME &&
                    !it.name.endsWith(PENDING_SUFFIX)
            }
            ?.associateBy { it.name }
            ?.toMutableMap()
            ?: mutableMapOf()
        var failedFiles = 0

        fun migrateSource(
            sourceLocation: String,
            sourceKey: String,
            displayName: String,
            sourceSize: Long,
        ): Boolean = runCatching {
            val safeDisplayName = sanitizeDisplayName(displayName)
            val existing = existingByDisplayName[safeDisplayName]
                ?.takeIf { it.length() > 0L && it.length() == sourceSize }
            val destination = existing?.absolutePath ?: checkNotNull(
                openInputStream(appContext, sourceLocation),
            ) { "无法读取历史截图" }.use { input ->
                createEntry(
                    context = appContext,
                    displayName = safeDisplayName,
                ) { output ->
                    input.copyTo(output)
                }.also { createdLocation ->
                    createdLocations += createdLocation
                    existingByDisplayName[safeDisplayName] = File(createdLocation)
                }
            }
            migratedLocations[sourceKey] = destination
        }.onFailure {
            failedFiles++
            AppLogger.update("Screenshot migration failed: $sourceLocation, ${it.message}")
        }.isSuccess

        val legacyInternal = legacyInternalDirectory(appContext)
        if (canonicalPathOrAbsolute(legacyInternal) != canonicalPathOrAbsolute(targetDirectory)) {
            legacyInternal.walkTopDown().filter(File::isFile).forEach { source ->
                val sourceKey = canonicalPathOrAbsolute(source)
                if (
                    migrateSource(
                        sourceLocation = source.absolutePath,
                        sourceKey = sourceKey,
                        displayName = source.name,
                        sourceSize = source.length(),
                    )
                ) {
                    legacyFilesToDelete += source
                }
            }
        }

        fun migrateMediaCollection(collection: Uri, path: String) {
            runCatching { queryMediaEntries(appContext.contentResolver, collection, path) }
                .onSuccess { entries ->
                    entries.forEach { source ->
                        val sourceLocation = source.uri.toString()
                        if (
                            migrateSource(
                                sourceLocation = sourceLocation,
                                sourceKey = sourceLocation,
                                displayName = source.displayName,
                                sourceSize = source.size,
                            )
                        ) {
                            publicLocationsToDelete += sourceLocation
                        }
                    }
                }
                .onFailure {
                    failedFiles++
                    AppLogger.update("Screenshot MediaStore query failed: $path, ${it.message}")
                }
        }

        migrateMediaCollection(imagesCollection(), publicPicturesRelativePath)
        migrateMediaCollection(downloadsCollection(), legacyDownloadsRelativePath)

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

        legacyFilesToDelete.forEach { file ->
            if (file.exists() && !file.delete()) {
                failedFiles++
                AppLogger.update("Screenshot legacy file delete failed: ${file.absolutePath}")
            }
        }
        publicLocationsToDelete.forEach { location ->
            if (!delete(appContext, location)) {
                failedFiles++
                AppLogger.update("Screenshot MediaStore delete failed: $location")
            }
        }
        if (canonicalPathOrAbsolute(legacyInternal) != canonicalPathOrAbsolute(targetDirectory)) {
            legacyInternal.walkBottomUp()
                .filter(File::isDirectory)
                .forEach { directory -> if (directory.listFiles().isNullOrEmpty()) directory.delete() }
        }

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
        write: (OutputStream) -> Unit,
    ): String {
        val directory = prepareStorageDirectory(context.applicationContext)
        val target = availableTargetFile(directory, sanitizeDisplayName(displayName))
        val pending = File(
            directory,
            ".${target.name}.${UUID.randomUUID().toString().take(6)}$PENDING_SUFFIX",
        )
        try {
            pending.outputStream().buffered().use(write)
            check(pending.renameTo(target)) { "无法完成截图写入" }
            return target.absolutePath
        } catch (error: Exception) {
            runCatching { pending.delete() }
            throw error
        }
    }

    private fun storageDirectory(context: Context): File {
        val externalPictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return externalPictures?.let { File(it, DIRECTORY_NAME) }
            ?: legacyInternalDirectory(context)
    }

    private fun prepareStorageDirectory(context: Context): File {
        val directory = storageDirectory(context)
        check(directory.isDirectory || directory.mkdirs()) { "无法创建截图存储目录" }
        val noMedia = File(directory, NO_MEDIA_FILE_NAME)
        if (!noMedia.exists()) {
            runCatching { noMedia.createNewFile() }
                .onFailure { AppLogger.update("Screenshot .nomedia create failed: ${it.message}") }
        }
        return directory
    }

    private fun legacyInternalDirectory(context: Context): File =
        File(context.filesDir, LEGACY_INTERNAL_DIRECTORY_NAME)

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
        if (location.isBlank()) return null
        migratedLocations[location]?.let { return it }
        if (locationUri(location) != null) return null
        return migratedLocations[canonicalPathOrAbsolute(File(location))]
    }

    private fun locationUri(location: String): Uri? {
        val parsed = Uri.parse(location)
        return parsed.takeIf { it.scheme == "content" || it.scheme == "file" }
    }

    private fun sanitizeDisplayName(displayName: String): String {
        val leafName = displayName.substringAfterLast('/').substringAfterLast('\\')
        val extension = normalizeExtension(leafName.substringAfterLast('.', "png"))
        val baseName = leafName.substringBeforeLast('.', leafName)
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), "_")
            .trim('.', ' ', '_')
            .take(100)
            .ifBlank { "识别截图" }
        return "$baseName.$extension"
    }

    private fun availableTargetFile(directory: File, displayName: String): File {
        val preferred = File(directory, displayName)
        if (!preferred.exists()) return preferred
        val extension = normalizeExtension(displayName.substringAfterLast('.', "png"))
        val baseName = displayName.substringBeforeLast('.', displayName).take(90)
        return generateSequence {
            File(directory, "${baseName}_${UUID.randomUUID().toString().take(6)}.$extension")
        }.first { !it.exists() }
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

    private fun readDirectoryStats(directory: File): Stats {
        if (!directory.isDirectory) return Stats()
        var size = 0L
        var count = 0
        directory.walkTopDown()
            .filter {
                it.isFile &&
                    it.name != NO_MEDIA_FILE_NAME &&
                    !it.name.endsWith(PENDING_SUFFIX)
            }
            .forEach { file ->
                size += file.length().coerceAtLeast(0L)
                count++
            }
        return Stats(size, count)
    }

    private fun clearDirectory(directory: File): Boolean {
        if (!directory.exists()) return true
        var success = true
        directory.listFiles()?.forEach { child ->
            if (!child.deleteRecursively()) success = false
        }
        return success
    }

    private fun cleanupPendingFiles(directory: File) {
        val staleBefore = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        directory.listFiles()
            ?.filter {
                it.isFile &&
                    it.name.endsWith(PENDING_SUFFIX) &&
                    it.lastModified() in 1L..staleBefore
            }
            ?.forEach { it.delete() }
    }

    private fun canonicalPathOrAbsolute(file: File): String =
        runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)

    private fun readCollectionStats(
        resolver: ContentResolver,
        collection: Uri,
        path: String,
    ): Stats = runCatching {
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns.SIZE),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(path),
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

    private fun deleteCollection(
        resolver: ContentResolver,
        collection: Uri,
        path: String,
    ): Boolean = runCatching {
        resolver.delete(
            collection,
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(path),
        )
        true
    }.getOrDefault(false)

    private fun queryMediaEntries(
        resolver: ContentResolver,
        collection: Uri,
        path: String,
    ): List<MediaEntry> {
        val projection = arrayOf(
            BaseColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
        )
        return resolver.query(
            collection,
            projection,
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0",
            arrayOf(path),
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        MediaEntry(
                            uri = ContentUris.withAppendedId(collection, cursor.getLong(idIndex)),
                            displayName = cursor.getString(nameIndex),
                            size = cursor.getLong(sizeIndex).coerceAtLeast(0L),
                        ),
                    )
                }
            }
        } ?: emptyList()
    }

    private fun imagesCollection(): Uri =
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    private fun downloadsCollection(): Uri =
        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
}
