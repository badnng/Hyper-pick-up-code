package com.Badnng.moe.helper

import android.content.Context
import androidx.room.withTransaction
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.data.db.OrderGroup
import com.Badnng.moe.recognition.RecognitionDiagnosticRedactor
import com.Badnng.moe.recognition.SecureApiKeyStore
import com.Badnng.moe.rules.RecognitionRuleEngine
import com.Badnng.moe.rules.RuleValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object BackupManager {
    private const val FORMAT_VERSION = 2
    private const val MANIFEST_ENTRY = "manifest.json"
    private const val ORDERS_ENTRY = "data/orders.json"
    private const val GROUPS_ENTRY = "data/groups.json"
    private const val SETTINGS_ENTRY = "data/settings.json"
    private const val SECRETS_ENTRY = "secure/api_keys.enc"
    private const val RULES_PREFIX = "rules/"
    private const val SCREENSHOTS_PREFIX = "screenshots/"
    private const val MAX_ARCHIVE_BYTES = 1_073_741_824L
    private const val MAX_JSON_ENTRY_BYTES = 20L * 1024 * 1024
    private const val MAX_SECRET_ENTRY_BYTES = 2L * 1024 * 1024
    private val SCREENSHOT_ENTRY_PATTERN =
        Regex("^screenshots/[0-9a-f]{64}\\.(?:png|jpe?g|webp|img)$")
    private val SHA256_PATTERN = Regex("^[0-9a-fA-F]{64}$")

    suspend fun createBackup(
        context: Context,
        output: OutputStream,
        options: BackupOptions,
        password: String? = null,
    ) = withContext(Dispatchers.IO) {
        require(options.includeOrders || options.includeSettings || options.includeRules || options.includeApiKeys) {
            "请至少选择一项备份内容"
        }
        if (options.includeApiKeys) {
            require(!password.isNullOrBlank() && password.length >= 8) { "备份密码至少需要 8 位" }
        }

        val appContext = context.applicationContext
        val database = OrderDatabase.getDatabase(appContext)
        val orders = if (options.includeOrders) database.orderDao().getAllOrdersList() else emptyList()
        val groups = if (options.includeOrders) database.orderGroupDao().getAllGroupsList() else emptyList()
        val settings = if (options.includeSettings) {
            BackupSettingsPolicy.collect(appContext.getSharedPreferences("settings", Context.MODE_PRIVATE))
        } else emptyMap()
        val ruleFiles = if (options.includeRules) collectRuleFiles(appContext) else emptyMap()
        val screenshotAssets = if (options.includeOrders && options.includeScreenshots) {
            collectScreenshotAssets(appContext, orders, groups)
        } else ScreenshotAssets.EMPTY
        val apiKeys = if (options.includeOrders || options.includeApiKeys) {
            SecureApiKeyStore(appContext).exportApiKeys()
        } else emptyMap()

        val checksums = linkedMapOf<String, String>()
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            if (options.includeOrders) {
                writeBytesEntry(
                    zip,
                    ORDERS_ENTRY,
                    encodeOrders(orders, screenshotAssets.pathToEntry, apiKeys.values)
                        .toString(2).toByteArray(Charsets.UTF_8),
                    checksums,
                )
                writeBytesEntry(
                    zip,
                    GROUPS_ENTRY,
                    encodeGroups(groups, screenshotAssets.pathToEntry).toString(2).toByteArray(Charsets.UTF_8),
                    checksums,
                )
            }
            if (options.includeSettings) {
                writeBytesEntry(
                    zip,
                    SETTINGS_ENTRY,
                    BackupSettingsPolicy.encode(settings).toString(2).toByteArray(Charsets.UTF_8),
                    checksums,
                )
            }
            ruleFiles.forEach { (entryName, bytes) -> writeBytesEntry(zip, entryName, bytes, checksums) }
            screenshotAssets.entryToLocation.forEach { (entryName, location) ->
                writeScreenshotEntry(zip, entryName, appContext, location, checksums)
            }
            if (options.includeApiKeys) {
                val secrets = JSONObject(apiKeys)
                val encrypted = BackupSecretCrypto.encrypt(
                    secrets.toString().toByteArray(Charsets.UTF_8),
                    password!!,
                )
                writeBytesEntry(zip, SECRETS_ENTRY, encrypted, checksums)
            }

            val manifest = JSONObject().apply {
                put("formatVersion", FORMAT_VERSION)
                put("createdAt", System.currentTimeMillis())
                put("appVersion", appVersion(appContext))
                put("sections", JSONObject().apply {
                    put("orders", options.includeOrders)
                    put("settings", options.includeSettings)
                    put("rules", options.includeRules)
                    put("screenshots", screenshotAssets.entryToLocation.isNotEmpty())
                    put("apiKeys", options.includeApiKeys)
                })
                put("counts", JSONObject().apply {
                    put("orders", orders.size)
                    put("groups", groups.size)
                    put("settings", settings.size)
                    put("ruleFiles", ruleFiles.size)
                    put("screenshots", screenshotAssets.entryToLocation.size)
                })
                put("checksums", JSONObject().apply {
                    checksums.forEach { (name, checksum) -> put(name, checksum) }
                })
            }
            writeRawEntry(zip, MANIFEST_ENTRY, manifest.toString(2).toByteArray(Charsets.UTF_8))
        }
    }

    suspend fun inspectBackup(context: Context, input: InputStream): StagedBackup =
        withContext(Dispatchers.IO) {
            val stagingDir = File(context.cacheDir, "backup-staging").apply { mkdirs() }
            val archive = File(stagingDir, "${UUID.randomUUID()}.backup")
            try {
                copyWithLimit(input, FileOutputStream(archive), MAX_ARCHIVE_BYTES)
                val staged = ZipFile(archive).use { zip ->
                    val entries = validateArchiveEntries(zip)
                    if (entries.containsKey(MANIFEST_ENTRY)) {
                        inspectV2(archive, zip, entries)
                    } else {
                        inspectV1(archive, zip, entries)
                    }
                }
                enrichPreview(context.applicationContext, staged)
            } catch (error: Exception) {
                archive.delete()
                throw error
            }
        }

    suspend fun restoreBackup(
        context: Context,
        stagedBackup: StagedBackup,
        selection: RestoreSelection,
        password: String? = null,
    ): RestoreReport = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val payload = stagedBackup.payload
        val apiKeys = if (selection.restoreApiKeys && payload.hasEncryptedSecrets) {
            require(!password.isNullOrBlank()) { "请输入备份密码" }
            decryptApiKeys(stagedBackup.archiveFile, password)
        } else emptyMap()
        val normalizedSettings = if (selection.restoreSettings) {
            BackupSettingsPolicy.normalize(appContext, payload.settings)
        } else NormalizedSettings(emptyMap(), emptyList())

        val restoreRoot = File(appContext.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        val rulesSnapshot = File(restoreRoot, "rules-snapshot")
        val rulesDir = File(appContext.filesDir, "rules")
        val preferences = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val preferenceSnapshot = if (selection.restoreSettings) preferences.all.toMap() else emptyMap()
        val apiKeyStore = SecureApiKeyStore(appContext)
        val apiKeySnapshot = if (selection.restoreApiKeys) apiKeyStore.exportApiKeys() else emptyMap()
        val createdScreenshots = mutableListOf<String>()
        var restoredScreenshotCount = 0
        var insertedOrders = 0
        var overwrittenOrders = 0
        var skippedOrders = 0
        var restoredGroups = 0
        var failedItems = 0
        val restoreAdjustments = normalizedSettings.adjustments.toMutableList()
        if (selection.restoreRules && rulesDir.exists()) {
            rulesDir.copyRecursively(rulesSnapshot, overwrite = true)
        }

        try {
            val screenshotPaths = if (
                selection.restoreOrders && selection.restoreScreenshots && payload.screenshotEntries.isNotEmpty()
            ) {
                restoreScreenshots(
                    stagedBackup.archiveFile,
                    payload.screenshotEntries,
                    appContext,
                    createdScreenshots,
                ).also { restoredScreenshotCount = it.size }
            } else emptyMap()

            if (selection.restoreSettings) {
                val editor = preferences.edit()
                preferences.all.keys.filter(BackupSettingsPolicy::isPortableKey).forEach(editor::remove)
                BackupSettingsPolicy.write(editor, normalizedSettings.values)
                check(editor.commit()) { "写入设置失败" }
            }
            if (selection.restoreRules) replaceRules(rulesDir, payload.ruleEntries)
            if (selection.restoreApiKeys && payload.hasEncryptedSecrets) apiKeyStore.replaceApiKeys(apiKeys)

            if (selection.restoreOrders) {
                val database = OrderDatabase.getDatabase(appContext)
                val orderDao = database.orderDao()
                val groupDao = database.orderGroupDao()
                database.withTransaction {
                    val existingIds = orderDao.getAllOrdersList().mapTo(hashSetOf()) { it.id }
                    if (selection.orderPolicy == RestoreOrderPolicy.REPLACE_ALL) {
                        overwrittenOrders = existingIds.intersect(payload.orders.map { it.order.id }.toSet()).size
                        insertedOrders = payload.orders.size - overwrittenOrders
                        orderDao.deleteAllOrders()
                        groupDao.deleteAllGroups()
                        val validGroupIds = hashSetOf<Long>()
                        val orderCountsByGroup = payload.orders.groupingBy { it.order.groupId }.eachCount()
                        payload.groups.forEach { backedGroup ->
                            val restored = backedGroup.group.copy(
                                screenshotPath = backedGroup.screenshotEntry?.let(screenshotPaths::get).orEmpty(),
                                orderCount = orderCountsByGroup[backedGroup.group.id] ?: 0,
                            )
                            groupDao.insertGroup(restored)
                            validGroupIds += restored.id
                            restoredGroups++
                        }
                        payload.orders.forEach { backedOrder ->
                            orderDao.insertOrReplace(
                                backedOrder.order.copy(
                                    screenshotPath = restoredScreenshotPath(appContext, backedOrder, screenshotPaths),
                                    groupId = backedOrder.order.groupId?.takeIf(validGroupIds::contains),
                                ),
                            )
                        }
                    } else {
                        val pendingOrders = payload.orders.filter { it.order.id !in existingIds }
                        skippedOrders = payload.orders.size - pendingOrders.size
                        val pendingByGroup = pendingOrders.groupBy { it.order.groupId }
                        val remappedGroupIds = mutableMapOf<Long, Long>()
                        payload.groups.forEach { backedGroup ->
                            val members = pendingByGroup[backedGroup.group.id].orEmpty()
                            if (members.isNotEmpty()) {
                                val newId = groupDao.insertGroup(
                                    backedGroup.group.copy(
                                        id = 0,
                                        orderCount = members.size,
                                        screenshotPath = backedGroup.screenshotEntry?.let(screenshotPaths::get).orEmpty(),
                                    ),
                                )
                                remappedGroupIds[backedGroup.group.id] = newId
                                restoredGroups++
                            }
                        }
                        pendingOrders.forEach { backedOrder ->
                            orderDao.insert(
                                backedOrder.order.copy(
                                    screenshotPath = restoredScreenshotPath(appContext, backedOrder, screenshotPaths),
                                    groupId = backedOrder.order.groupId?.let(remappedGroupIds::get),
                                ),
                            )
                            insertedOrders++
                        }
                    }
                }
            }
        } catch (error: Exception) {
            if (selection.restoreSettings) {
                runCatching {
                    val rollbackEditor = preferences.edit().clear()
                    BackupSettingsPolicy.write(rollbackEditor, preferenceSnapshot)
                    check(rollbackEditor.commit()) { "回滚设置失败" }
                }.onFailure(error::addSuppressed)
            }
            if (selection.restoreRules) {
                runCatching {
                    rulesDir.deleteRecursively()
                    if (rulesSnapshot.exists()) rulesSnapshot.copyRecursively(rulesDir, overwrite = true)
                }.onFailure(error::addSuppressed)
            }
            if (selection.restoreApiKeys) {
                runCatching { apiKeyStore.replaceApiKeys(apiKeySnapshot) }
                    .onFailure(error::addSuppressed)
            }
            createdScreenshots.forEach { location ->
                runCatching { ScreenshotStorage.delete(appContext, location) }.onFailure(error::addSuppressed)
            }
            throw error
        } finally {
            runCatching { restoreRoot.deleteRecursively() }
        }

        if (selection.restoreRules) {
            runCatching { RecognitionRuleEngine.reload(appContext) }
                .onFailure {
                    failedItems++
                    restoreAdjustments += "规则文件已恢复，但本次重新加载失败，将在下次启动时重试"
                    AppLogger.update("Backup restore rule reload failed: ${it.message}")
                }
        }
        RestoreReport(
            insertedOrders = insertedOrders,
            overwrittenOrders = overwrittenOrders,
            skippedOrders = skippedOrders,
            restoredGroups = restoredGroups,
            restoredSettings = if (selection.restoreSettings) {
                normalizedSettings.values.keys.count(BackupSettingsPolicy::isPortableKey)
            } else 0,
            restoredRuleFiles = if (selection.restoreRules) payload.ruleEntries.size else 0,
            restoredScreenshots = restoredScreenshotCount,
            restoredApiKeys = if (selection.restoreApiKeys) apiKeys.size else 0,
            adjustments = restoreAdjustments,
            failedItems = failedItems,
        )
    }

    fun generateBackupFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        return "澎湃记备份-$timestamp.backup"
    }

    private fun inspectV2(
        archive: File,
        zip: ZipFile,
        entries: Map<String, java.util.zip.ZipEntry>,
    ): StagedBackup {
        val manifest = JSONObject(readText(zip, entries.getValue(MANIFEST_ENTRY), MAX_JSON_ENTRY_BYTES))
        require(manifest.optInt("formatVersion") == FORMAT_VERSION) { "不支持的备份版本" }
        verifyChecksums(zip, entries, manifest.optJSONObject("checksums") ?: JSONObject())
        val orders = entries[ORDERS_ENTRY]?.let {
            parseOrders(JSONArray(readText(zip, it, MAX_JSON_ENTRY_BYTES)), legacy = false)
        }.orEmpty()
        val groups = entries[GROUPS_ENTRY]?.let {
            parseGroups(JSONArray(readText(zip, it, MAX_JSON_ENTRY_BYTES)))
        }.orEmpty()
        require(orders.map { it.order.id }.distinct().size == orders.size) {
            "备份中存在重复订单 ID"
        }
        require(groups.map { it.group.id }.distinct().size == groups.size) {
            "备份中存在重复订单组 ID"
        }
        val settings = entries[SETTINGS_ENTRY]?.let {
            BackupSettingsPolicy.decode(JSONObject(readText(zip, it, MAX_JSON_ENTRY_BYTES)))
        }.orEmpty()
        val rules = entries.filterKeys { it.startsWith(RULES_PREFIX) }.mapValues { (name, entry) ->
            readText(zip, entry, MAX_JSON_ENTRY_BYTES).also { validateRuleEntry(name, it) }
        }
        val screenshots = entries.keys.filterTo(linkedSetOf()) { it.startsWith(SCREENSHOTS_PREFIX) }
        screenshots.forEach { entryName ->
            require(SCREENSHOT_ENTRY_PATTERN.matches(entryName)) { "截图条目名称无效: $entryName" }
        }
        val hasSecrets = entries.containsKey(SECRETS_ENTRY)
        val counts = manifest.optJSONObject("counts") ?: JSONObject()
        val warnings = buildList {
            if (counts.optInt("orders", orders.size) != orders.size) {
                add("订单计数与清单不一致，已按实际内容读取")
            }
            if (counts.optInt("groups", groups.size) != groups.size) {
                add("订单组计数与清单不一致，已按实际内容读取")
            }
            if (counts.optInt("settings", settings.size) != settings.size) {
                add("设置计数与清单不一致，已按实际内容读取")
            }
            if (counts.optInt("ruleFiles", rules.size) != rules.size) {
                add("规则文件计数与清单不一致，已按实际内容读取")
            }
            if (counts.optInt("screenshots", screenshots.size) != screenshots.size) {
                add("截图计数与清单不一致，已按实际内容读取")
            }
            if (orders.any { it.screenshotEntry != null && it.screenshotEntry !in screenshots }) {
                add("部分订单引用的截图不在备份中")
            }
            if (groups.any { it.screenshotEntry != null && it.screenshotEntry !in screenshots }) {
                add("部分订单组引用的截图不在备份中")
            }
            val groupIds = groups.mapTo(hashSetOf()) { it.group.id }
            if (orders.any { it.order.groupId != null && it.order.groupId !in groupIds }) {
                add("部分订单引用的订单组不存在，恢复后将转为未分组订单")
            }
        }
        return StagedBackup(
            archive,
            BackupPayload(FORMAT_VERSION, orders, groups, settings, rules, screenshots, hasSecrets),
            BackupPreview(
                formatVersion = FORMAT_VERSION,
                appVersion = manifest.optString("appVersion", "未知"),
                createdAt = manifest.optLong("createdAt", 0L),
                orderCount = orders.size,
                groupCount = groups.size,
                settingsCount = settings.size,
                ruleFileCount = rules.size,
                screenshotCount = screenshots.size,
                hasApiKeys = hasSecrets,
                isLegacy = false,
                archiveSizeBytes = archive.length(),
                warnings = warnings,
            ),
        )
    }

    private fun inspectV1(
        archive: File,
        zip: ZipFile,
        entries: Map<String, java.util.zip.ZipEntry>,
    ): StagedBackup {
        require(entries.containsKey("orders.json") || entries.containsKey("settings.json") || entries.containsKey("rules.json")) {
            "无法识别的备份文件"
        }
        val orders = entries["orders.json"]?.let {
            parseOrders(JSONArray(readText(zip, it, MAX_JSON_ENTRY_BYTES)), legacy = true)
        }.orEmpty()
        val settings = entries["settings.json"]?.let {
            BackupSettingsPolicy.decodeLegacy(JSONObject(readText(zip, it, MAX_JSON_ENTRY_BYTES)))
        }.orEmpty()
        val rules = entries["rules.json"]?.let {
            val text = readText(zip, it, MAX_JSON_ENTRY_BYTES)
            validateRuleEntry("rules/rules.json", text)
            mapOf("rules/rules.json" to text)
        }.orEmpty()
        return StagedBackup(
            archive,
            BackupPayload(1, orders, emptyList(), settings, rules, emptySet(), false),
            BackupPreview(
                formatVersion = 1,
                appVersion = "旧版未记录",
                createdAt = archive.lastModified(),
                orderCount = orders.size,
                groupCount = 0,
                settingsCount = settings.size,
                ruleFileCount = rules.size,
                screenshotCount = 0,
                hasApiKeys = false,
                isLegacy = true,
                archiveSizeBytes = archive.length(),
                warnings = listOf("旧版备份不包含订单组、截图文件和识别诊断字段"),
            ),
        )
    }

    private suspend fun enrichPreview(context: Context, staged: StagedBackup): StagedBackup {
        val payload = staged.payload
        val existingOrderIds = if (payload.orders.isEmpty()) {
            emptySet()
        } else {
            OrderDatabase.getDatabase(context).orderDao().getAllOrdersList()
                .mapTo(hashSetOf()) { it.id }
        }
        val conflicts = payload.orders.count { it.order.id in existingOrderIds }
        val compatibilityAdjustments = if (payload.settings.isEmpty()) {
            emptyList()
        } else {
            BackupSettingsPolicy.normalize(context, payload.settings).adjustments
        }
        return StagedBackup(
            archiveFile = staged.archiveFile,
            payload = payload,
            preview = staged.preview.copy(
                conflictingOrderCount = conflicts,
                compatibilityAdjustments = compatibilityAdjustments,
            ),
        )
    }

    private fun validateArchiveEntries(zip: ZipFile): Map<String, java.util.zip.ZipEntry> {
        val result = linkedMapOf<String, java.util.zip.ZipEntry>()
        var totalSize = 0L
        val iterator = zip.entries()
        while (iterator.hasMoreElements()) {
            val entry = iterator.nextElement()
            if (entry.isDirectory) continue
            validateEntryName(entry.name)
            require(result.put(entry.name, entry) == null) { "备份中存在重复文件: ${entry.name}" }
            if (entry.size > 0) {
                require(entry.size <= MAX_ARCHIVE_BYTES && totalSize <= MAX_ARCHIVE_BYTES - entry.size) {
                    "备份解压后体积过大"
                }
                totalSize += entry.size
            }
        }
        return result
    }

    private fun verifyChecksums(
        zip: ZipFile,
        entries: Map<String, java.util.zip.ZipEntry>,
        expected: JSONObject,
    ) {
        entries.filterKeys { it != MANIFEST_ENTRY }.forEach { (name, entry) ->
            val expectedHash = expected.optString(name).takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("备份清单缺少校验值: $name")
            require(expectedHash.matches(SHA256_PATTERN)) {
                "备份清单校验值格式无效: $name"
            }
            val actualHash = zip.getInputStream(entry).use(::sha256)
            require(expectedHash.equals(actualHash, ignoreCase = true)) { "备份文件校验失败: $name" }
            if (name.startsWith(SCREENSHOTS_PREFIX)) {
                val contentHash = name.substringAfterLast('/').substringBefore('.')
                require(contentHash.equals(actualHash, ignoreCase = true)) {
                    "截图内容哈希与文件名不一致: $name"
                }
            }
        }
    }

    private fun restoreScreenshots(
        archive: File,
        entries: Set<String>,
        context: Context,
        createdLocations: MutableList<String>,
    ): Map<String, String> {
        return ZipFile(archive).use { zip ->
            buildMap {
                entries.forEach { entryName ->
                    val entry = zip.getEntry(entryName) ?: return@forEach
                    val expectedHash = entryName.substringAfterLast('/').substringBefore('.')
                    val extension = entryName.substringAfterLast('.').lowercase(Locale.ROOT)
                    val location = zip.getInputStream(entry).use { input ->
                        ScreenshotStorage.saveStream(
                            context = context,
                            input = input,
                            namePrefix = "备份恢复_$expectedHash",
                            extension = extension,
                        )
                    }
                    createdLocations += location
                    put(entryName, location)
                }
            }
        }
    }

    private fun restoredScreenshotPath(
        context: Context,
        order: BackupOrder,
        paths: Map<String, String>,
    ): String = order.screenshotEntry?.let(paths::get)
        ?: order.order.screenshotPath.takeIf { ScreenshotStorage.exists(context, it) }.orEmpty()

    private fun replaceRules(rulesDir: File, entries: Map<String, String>) {
        rulesDir.deleteRecursively()
        rulesDir.mkdirs()
        entries.forEach { (entryName, text) ->
            val relative = entryName.removePrefix(RULES_PREFIX)
            validateEntryName(relative)
            File(rulesDir, relative).apply { parentFile?.mkdirs() }.writeText(text, Charsets.UTF_8)
        }
    }

    private fun decryptApiKeys(archive: File, password: String): Map<String, String> = ZipFile(archive).use { zip ->
        val entry = zip.getEntry(SECRETS_ENTRY) ?: return@use emptyMap()
        val json = JSONObject(
            BackupSecretCrypto.decrypt(readBytes(zip, entry, MAX_SECRET_ENTRY_BYTES), password)
                .toString(Charsets.UTF_8),
        )
        buildMap {
            json.keys().forEach { key -> json.optString(key).takeIf(String::isNotBlank)?.let { put(key, it) } }
        }
    }

    private fun collectRuleFiles(context: Context): Map<String, ByteArray> {
        val rulesDir = File(context.filesDir, "rules")
        if (!rulesDir.exists()) return emptyMap()
        return rulesDir.walkTopDown().filter(File::isFile).associate { file ->
            val relative = file.relativeTo(rulesDir).invariantSeparatorsPath
            validateEntryName(relative)
            val entryName = RULES_PREFIX + relative
            val bytes = file.readBytes()
            require(bytes.size <= MAX_JSON_ENTRY_BYTES) { "规则文件过大: $relative" }
            validateRuleEntry(entryName, bytes.toString(Charsets.UTF_8))
            entryName to bytes
        }
    }

    private fun collectScreenshotAssets(
        context: Context,
        orders: List<OrderEntity>,
        groups: List<OrderGroup>,
    ): ScreenshotAssets {
        val candidates = (orders.map { it.screenshotPath } + groups.map { it.screenshotPath })
            .asSequence()
            .filter(String::isNotBlank)
            .distinct()
            .filter { ScreenshotStorage.exists(context, it) }
            .toList()
        val pathToEntry = mutableMapOf<String, String>()
        val entryToLocation = linkedMapOf<String, String>()
        val hashToEntry = mutableMapOf<String, String>()
        candidates.forEach { location ->
            val hash = ScreenshotStorage.openInputStream(context, location)?.use(::sha256)
                ?: return@forEach
            val extension = ScreenshotStorage.extension(context, location)
            val entry = hashToEntry.getOrPut(hash) { "$SCREENSHOTS_PREFIX$hash.$extension" }
            pathToEntry[location] = entry
            entryToLocation.putIfAbsent(entry, location)
        }
        return ScreenshotAssets(pathToEntry, entryToLocation)
    }

    private fun encodeOrders(
        orders: List<OrderEntity>,
        screenshotEntries: Map<String, String>,
        knownSecrets: Collection<String>,
    ): JSONArray =
        JSONArray().apply {
            orders.forEach { order ->
                put(JSONObject().apply {
                    put("id", order.id); put("takeoutCode", order.takeoutCode)
                    putNullable("qrCodeData", order.qrCodeData)
                    putNullable("screenshotAsset", screenshotEntries[order.screenshotPath])
                    put("recognizedText", order.recognizedText); put("isCompleted", order.isCompleted)
                    put("createdAt", order.createdAt); putNullable("completedAt", order.completedAt)
                    put("orderType", order.orderType); putNullable("brandName", order.brandName)
                    putNullable("sourceApp", order.sourceApp); putNullable("sourcePackage", order.sourcePackage)
                    putNullable("fullText", order.fullText); putNullable("pickupLocation", order.pickupLocation)
                    putNullable("groupId", order.groupId); putNullable("recognitionMode", order.recognitionMode)
                    putNullable("recognitionInputType", order.recognitionInputType)
                    putNullable("recognitionTrigger", order.recognitionTrigger)
                    putNullable("recognitionProvider", order.recognitionProvider)
                    putNullable("recognitionModel", order.recognitionModel)
                    putNullable("recognitionUsedOfflineFallback", order.recognitionUsedOfflineFallback)
                    putNullable(
                        "recognitionError",
                        RecognitionDiagnosticRedactor.redact(order.recognitionError, knownSecrets),
                    )
                    putNullable(
                        "recognitionErrorDetail",
                        RecognitionDiagnosticRedactor.redact(order.recognitionErrorDetail, knownSecrets),
                    )
                    putNullable("recognitionDurationMs", order.recognitionDurationMs)
                    putNullable("ocrDiagnosticData", order.ocrDiagnosticData)
                    put("needsRuleCorrection", order.needsRuleCorrection)
                })
            }
        }

    private fun encodeGroups(groups: List<OrderGroup>, screenshotEntries: Map<String, String>): JSONArray =
        JSONArray().apply {
            groups.forEach { group ->
                put(JSONObject().apply {
                    put("id", group.id); put("name", group.name); put("orderType", group.orderType)
                    putNullable("brandName", group.brandName)
                    putNullable("screenshotAsset", screenshotEntries[group.screenshotPath])
                    putNullable("sourceApp", group.sourceApp); putNullable("sourcePackage", group.sourcePackage)
                    put("recognizedText", group.recognizedText); put("createdAt", group.createdAt)
                    put("isCompleted", group.isCompleted); putNullable("completedAt", group.completedAt)
                    put("orderCount", group.orderCount); putNullable("iconResName", group.iconResName)
                })
            }
        }

    private fun parseOrders(array: JSONArray, legacy: Boolean): List<BackupOrder> = buildList {
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            val id = json.optNullableString("id") ?: continue
            val code = json.optNullableString("takeoutCode") ?: continue
            val legacyPath = json.optString("screenshotPath")
                .takeIf { legacy && it.isNotBlank() && File(it).isFile }.orEmpty()
            add(BackupOrder(
                order = OrderEntity(
                    id = id, takeoutCode = code, qrCodeData = json.optNullableString("qrCodeData"),
                    screenshotPath = legacyPath, recognizedText = json.optString("recognizedText", ""),
                    isCompleted = json.optBoolean("isCompleted", false), createdAt = json.optLong("createdAt", 0L),
                    completedAt = json.optNullableLong("completedAt"), orderType = json.optString("orderType", "餐食"),
                    brandName = json.optNullableString("brandName"), sourceApp = json.optNullableString("sourceApp"),
                    sourcePackage = json.optNullableString("sourcePackage"), fullText = json.optNullableString("fullText"),
                    pickupLocation = json.optNullableString("pickupLocation"), groupId = json.optNullableLong("groupId"),
                    recognitionMode = json.optNullableString("recognitionMode"),
                    recognitionInputType = json.optNullableString("recognitionInputType"),
                    recognitionTrigger = json.optNullableString("recognitionTrigger"),
                    recognitionProvider = json.optNullableString("recognitionProvider"),
                    recognitionModel = json.optNullableString("recognitionModel"),
                    recognitionUsedOfflineFallback = json.optNullableBoolean("recognitionUsedOfflineFallback"),
                    recognitionError = json.optNullableString("recognitionError"),
                    recognitionErrorDetail = json.optNullableString("recognitionErrorDetail"),
                    recognitionDurationMs = json.optNullableLong("recognitionDurationMs"),
                    ocrDiagnosticData = json.optNullableString("ocrDiagnosticData"),
                    needsRuleCorrection = json.optBoolean("needsRuleCorrection", false),
                ),
                screenshotEntry = json.optNullableString("screenshotAsset"),
            ))
        }
    }

    private fun parseGroups(array: JSONArray): List<BackupGroup> = buildList {
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            val id = json.optLong("id", 0L)
            if (id <= 0L) continue
            add(BackupGroup(
                group = OrderGroup(
                    id = id, name = json.optString("name", "订单组"),
                    orderType = json.optString("orderType", "餐食"), brandName = json.optNullableString("brandName"),
                    screenshotPath = "", sourceApp = json.optNullableString("sourceApp"),
                    sourcePackage = json.optNullableString("sourcePackage"),
                    recognizedText = json.optString("recognizedText", ""), createdAt = json.optLong("createdAt", 0L),
                    isCompleted = json.optBoolean("isCompleted", false), completedAt = json.optNullableLong("completedAt"),
                    orderCount = json.optInt("orderCount", 0), iconResName = json.optNullableString("iconResName"),
                ),
                screenshotEntry = json.optNullableString("screenshotAsset"),
            ))
        }
    }

    private fun validateRuleEntry(entryName: String, text: String) {
        runCatching { JSONObject(text) }.getOrElse { throw IllegalArgumentException("规则文件 JSON 无效: $entryName") }
        val relative = entryName.removePrefix(RULES_PREFIX)
        val isRecognitionRule = relative == "rules.json" || relative == "local_custom_rules.json" ||
            relative == "online_cache.json" || relative.startsWith("local_custom/") ||
            (relative.startsWith("online_sources/") && !relative.endsWith("_meta.json"))
        if (isRecognitionRule) {
            val validation = RuleValidator.validateJson(text)
            require(validation.isValid) { "规则文件校验失败: $entryName (${validation.errors.joinToString()})" }
        }
    }

    private fun writeBytesEntry(
        zip: ZipOutputStream,
        name: String,
        bytes: ByteArray,
        checksums: MutableMap<String, String>,
    ) {
        writeRawEntry(zip, name, bytes)
        checksums[name] = sha256(bytes)
    }

    private fun writeRawEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        validateEntryName(name)
        zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
    }

    private fun writeScreenshotEntry(
        zip: ZipOutputStream,
        name: String,
        context: Context,
        location: String,
        checksums: MutableMap<String, String>,
    ) {
        validateEntryName(name)
        val digest = MessageDigest.getInstance("SHA-256")
        zip.putNextEntry(ZipEntry(name))
        val source = checkNotNull(ScreenshotStorage.openInputStream(context, location)) {
            "无法读取截图: $location"
        }
        DigestInputStream(source, digest).use { input -> input.copyTo(zip) }
        zip.closeEntry()
        checksums[name] = digest.digest().toHexString()
    }

    private fun readText(zip: ZipFile, entry: java.util.zip.ZipEntry, limit: Long): String =
        readBytes(zip, entry, limit).toString(Charsets.UTF_8)

    private fun readBytes(zip: ZipFile, entry: java.util.zip.ZipEntry, limit: Long): ByteArray {
        require(entry.size <= limit || entry.size < 0L) { "备份条目过大: ${entry.name}" }
        return zip.getInputStream(entry).use { input ->
            java.io.ByteArrayOutputStream().use { output ->
                copyWithLimit(input, output, limit)
                output.toByteArray()
            }
        }
    }

    private fun copyWithLimit(input: InputStream, output: OutputStream, limit: Long) {
        BufferedInputStream(input).use { source ->
            BufferedOutputStream(output).use { target ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= limit) { "备份文件体积过大" }
                    target.write(buffer, 0, count)
                }
            }
        }
    }

    private fun validateEntryName(name: String) {
        require(name.isNotBlank() && !name.startsWith('/') && !name.startsWith('\\')) { "备份条目路径无效" }
        require('\\' !in name && ':' !in name && '\u0000' !in name &&
            name.split('/').none { it == "." || it == ".." || it.isBlank() }
        ) {
            "备份条目路径不安全: $name"
        }
    }

    private fun sha256(bytes: ByteArray): String = sha256(bytes.inputStream())
    private fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().toHexString()
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    private fun appVersion(context: Context): String = runCatching {
        @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty().ifBlank { "未知" }

    private fun JSONObject.putNullable(name: String, value: Any?) { put(name, value ?: JSONObject.NULL) }
    private fun JSONObject.optNullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else optString(name).takeIf(String::isNotBlank)
    private fun JSONObject.optNullableLong(name: String): Long? =
        if (!has(name) || isNull(name)) null else optLong(name)
    private fun JSONObject.optNullableBoolean(name: String): Boolean? =
        if (!has(name) || isNull(name)) null else optBoolean(name)

    private data class ScreenshotAssets(
        val pathToEntry: Map<String, String>,
        val entryToLocation: Map<String, String>,
    ) {
        companion object { val EMPTY = ScreenshotAssets(emptyMap(), emptyMap()) }
    }
}
