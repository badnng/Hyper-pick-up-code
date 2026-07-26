package com.Badnng.moe.helper

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.data.db.OrderGroup
import com.Badnng.moe.privacy.PrivacyConsent
import com.Badnng.moe.recognition.OnlineRecognitionPreferences
import com.Badnng.moe.recognition.OnlineRecognitionProvider
import com.Badnng.moe.recognition.SecureApiKeyStore
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class BackupManagerInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database by lazy { OrderDatabase.getDatabase(context) }

    @Before
    fun clearState() {
        runBlocking {
            database.orderDao().deleteAllOrders()
            database.orderGroupDao().deleteAllGroups()
            context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
            File(context.filesDir, "screenshots/backup-test").deleteRecursively()
            File(context.filesDir, "screenshots/restored").deleteRecursively()
            ScreenshotStorage.deleteAll(context)
        }
    }

    @After
    fun cleanUp() {
        runBlocking {
            database.orderDao().deleteAllOrders()
            database.orderGroupDao().deleteAllGroups()
            context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
            SecureApiKeyStore(context).replaceApiKeys(emptyMap())
            File(context.filesDir, "screenshots/backup-test").deleteRecursively()
            File(context.filesDir, "screenshots/restored").deleteRecursively()
            ScreenshotStorage.deleteAll(context)
        }
    }

    @Test
    fun versionTwoRoundTripDeduplicatesScreenshots() = runBlocking {
        val screenshot = File(context.filesDir, "screenshots/backup-test/shared.png").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        }
        val duplicateScreenshot = File(context.filesDir, "screenshots/backup-test/shared-copy.jpg").apply {
            writeBytes(screenshot.readBytes())
        }
        val groupId = database.orderGroupDao().insertGroup(
            OrderGroup(
                name = "测试组",
                orderType = "快递",
                screenshotPath = duplicateScreenshot.absolutePath,
                recognizedText = "测试",
                orderCount = 2,
            ),
        )
        val diagnosticSecret = "sk-diagnostic-secret-value"
        SecureApiKeyStore(context).save(OnlineRecognitionProvider.OPENAI, diagnosticSecret)
        listOf("A100", "A101").forEachIndexed { index, code ->
            database.orderDao().insert(
                OrderEntity(
                    id = "backup-order-$index",
                    takeoutCode = code,
                    screenshotPath = screenshot.absolutePath,
                    recognizedText = "测试",
                    orderType = "快递",
                    groupId = groupId,
                    recognitionErrorDetail = if (index == 0) {
                        "provider echoed api_key=$diagnosticSecret"
                    } else null,
                ),
            )
        }

        val output = ByteArrayOutputStream()
        BackupManager.createBackup(
            context,
            output,
            BackupOptions(
                includeOrders = true,
                includeSettings = false,
                includeRules = false,
                includeScreenshots = true,
            ),
        )
        val staged = BackupManager.inspectBackup(context, ByteArrayInputStream(output.toByteArray()))
        try {
            assertEquals(2, staged.preview.orderCount)
            assertEquals(1, staged.preview.groupCount)
            assertEquals(1, staged.preview.screenshotCount)
            assertEquals(2, staged.preview.conflictingOrderCount)
            assertFalse(
                staged.payload.orders.any {
                    it.order.recognitionErrorDetail.orEmpty().contains(diagnosticSecret)
                },
            )

            database.orderDao().deleteAllOrders()
            database.orderGroupDao().deleteAllGroups()
            val report = BackupManager.restoreBackup(
                context,
                staged,
                RestoreSelection(
                    restoreOrders = true,
                    restoreSettings = false,
                    restoreRules = false,
                    restoreScreenshots = true,
                    restoreApiKeys = false,
                ),
            )

            val restored = database.orderDao().getAllOrdersList()
            assertEquals(2, restored.size)
            assertEquals(1, restored.map { it.screenshotPath }.distinct().size)
            assertTrue(ScreenshotStorage.exists(context, restored.first().screenshotPath))
            assertEquals(2, report.insertedOrders)
            assertEquals(1, report.restoredGroups)
        } finally {
            staged.close()
        }
    }

    @Test
    fun legacyBackupUsesWhitelistAndDisablesOnlineMode() = runBlocking {
        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        preferences.edit()
            .putBoolean("onboarding_completed", false)
            .putBoolean(PrivacyConsent.ACCEPTED_KEY, true)
            .apply()
        val legacySettings = JSONObject().apply {
            put("haptic_enabled", false)
            put("onboarding_completed", true)
            put(PrivacyConsent.ACCEPTED_KEY, true)
            put(OnlineRecognitionPreferences.MODE_KEY, OnlineRecognitionPreferences.MODE_ONLINE)
        }
        val legacyOrders = JSONArray().put(
            JSONObject().apply {
                put("id", "legacy-order")
                put("takeoutCode", "L100")
                put("screenshotPath", "/path/that/does/not/exist.png")
                put("recognizedText", "自动识别")
            },
        )
        val archive = legacyArchive(legacyOrders, legacySettings)

        val staged = BackupManager.inspectBackup(context, ByteArrayInputStream(archive))
        try {
            assertTrue(staged.preview.isLegacy)
            assertEquals(1, staged.preview.orderCount)
            assertEquals(2, staged.preview.settingsCount)
            assertNull(staged.payload.orders.single().order.recognitionMode)
            assertTrue(staged.payload.orders.single().order.screenshotPath.isEmpty())
            BackupManager.restoreBackup(
                context,
                staged,
                RestoreSelection(
                    restoreOrders = false,
                    restoreSettings = true,
                    restoreRules = false,
                    restoreScreenshots = false,
                    restoreApiKeys = false,
                ),
            )

            assertFalse(preferences.getBoolean("haptic_enabled", true))
            assertFalse(preferences.getBoolean("onboarding_completed", true))
            assertFalse(PrivacyConsent.isAccepted(preferences))
            assertEquals(
                OnlineRecognitionPreferences.MODE_OFFLINE,
                preferences.getString(OnlineRecognitionPreferences.MODE_KEY, null),
            )
        } finally {
            staged.close()
        }
    }

    @Test
    fun versionTwoRejectsChecksumMismatchBeforeRestore() = runBlocking {
        val settings = "{}".toByteArray()
        val manifest = JSONObject().apply {
            put("formatVersion", 2)
            put("createdAt", 1L)
            put("appVersion", "test")
            put("counts", JSONObject())
            put("checksums", JSONObject().put("data/settings.json", "0".repeat(64)))
        }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("data/settings.json"))
            zip.write(settings)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString().toByteArray())
            zip.closeEntry()
        }

        val failure = runCatching {
            BackupManager.inspectBackup(context, ByteArrayInputStream(output.toByteArray()))
        }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("校验失败"))
    }

    @Test
    fun rejectsPathTraversalBeforeReadingPayload() = runBlocking {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("../settings.json"))
            zip.write("{}".toByteArray())
            zip.closeEntry()
        }

        val failure = runCatching {
            BackupManager.inspectBackup(context, ByteArrayInputStream(output.toByteArray()))
        }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("路径不安全"))
    }

    @Test
    fun rejectsInvalidLegacyRulesBeforeRestore() = runBlocking {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("rules.json"))
            zip.write("not-json".toByteArray())
            zip.closeEntry()
        }

        val failure = runCatching {
            BackupManager.inspectBackup(context, ByteArrayInputStream(output.toByteArray()))
        }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("JSON 无效"))
    }

    @Test
    fun wrongSecretPasswordStillAllowsRestoringOtherSections() = runBlocking {
        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val keyStore = SecureApiKeyStore(context)
        preferences.edit().putBoolean("haptic_enabled", false).commit()
        keyStore.save(OnlineRecognitionProvider.OPENAI, "sk-backup-test-value")
        val output = ByteArrayOutputStream()
        BackupManager.createBackup(
            context,
            output,
            BackupOptions(
                includeOrders = false,
                includeSettings = true,
                includeRules = false,
                includeApiKeys = true,
            ),
            password = "correct-password",
        )
        preferences.edit().putBoolean("haptic_enabled", true).commit()
        keyStore.replaceApiKeys(emptyMap())

        val staged = BackupManager.inspectBackup(context, ByteArrayInputStream(output.toByteArray()))
        try {
            val failure = runCatching {
                BackupManager.restoreBackup(
                    context,
                    staged,
                    RestoreSelection(
                        restoreOrders = false,
                        restoreSettings = true,
                        restoreRules = false,
                        restoreScreenshots = false,
                        restoreApiKeys = true,
                    ),
                    password = "wrong-password",
                )
            }.exceptionOrNull()
            assertNotNull(failure)
            assertTrue(preferences.getBoolean("haptic_enabled", false))
            assertNull(keyStore.get(OnlineRecognitionProvider.OPENAI))

            BackupManager.restoreBackup(
                context,
                staged,
                RestoreSelection(
                    restoreOrders = false,
                    restoreSettings = true,
                    restoreRules = false,
                    restoreScreenshots = false,
                    restoreApiKeys = false,
                ),
            )
            assertFalse(preferences.getBoolean("haptic_enabled", true))

            BackupManager.restoreBackup(
                context,
                staged,
                RestoreSelection(
                    restoreOrders = false,
                    restoreSettings = false,
                    restoreRules = false,
                    restoreScreenshots = false,
                    restoreApiKeys = true,
                ),
                password = "correct-password",
            )
            assertEquals("sk-backup-test-value", keyStore.get(OnlineRecognitionProvider.OPENAI))
        } finally {
            staged.close()
            keyStore.replaceApiKeys(emptyMap())
        }
    }

    private fun legacyArchive(orders: JSONArray, settings: JSONObject): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("orders.json"))
            zip.write(orders.toString().toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("settings.json"))
            zip.write(settings.toString().toByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }
}
