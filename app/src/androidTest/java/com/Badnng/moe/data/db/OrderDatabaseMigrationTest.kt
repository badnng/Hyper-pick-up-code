package com.Badnng.moe.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrderDatabaseMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var openHelper: SupportSQLiteOpenHelper

    @Before
    fun createVersionSixDatabase() {
        context.deleteDatabase(DATABASE_NAME)
        openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DATABASE_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE order_groups (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                name TEXT NOT NULL,
                                brandName TEXT,
                                orderType TEXT NOT NULL,
                                screenshotPath TEXT NOT NULL,
                                recognizedText TEXT NOT NULL,
                                sourceApp TEXT,
                                sourcePackage TEXT,
                                orderCount INTEGER NOT NULL DEFAULT 0,
                                isCompleted INTEGER NOT NULL DEFAULT 0,
                                createdAt INTEGER NOT NULL,
                                completedAt INTEGER,
                                iconResName TEXT
                            )
                            """.trimIndent(),
                        )
                        db.execSQL(
                            """
                            CREATE TABLE orders (
                                id TEXT NOT NULL PRIMARY KEY,
                                takeoutCode TEXT NOT NULL,
                                qrCodeData TEXT,
                                screenshotPath TEXT NOT NULL,
                                recognizedText TEXT NOT NULL,
                                orderType TEXT NOT NULL,
                                brandName TEXT,
                                pickupLocation TEXT,
                                fullText TEXT,
                                sourceApp TEXT,
                                sourcePackage TEXT,
                                isCompleted INTEGER NOT NULL,
                                createdAt INTEGER NOT NULL,
                                completedAt INTEGER,
                                groupId INTEGER,
                                recognitionMode TEXT,
                                FOREIGN KEY (groupId) REFERENCES order_groups(id) ON DELETE CASCADE
                            )
                            """.trimIndent(),
                        )
                        db.execSQL("CREATE INDEX index_orders_groupId ON orders(groupId)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        openHelper.writableDatabase
    }

    @After
    fun closeDatabase() {
        openHelper.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrationSixToSevenAddsEveryDiagnosticColumnAndToleratesExistingColumns() {
        val database = openHelper.writableDatabase

        OrderDatabase.MIGRATION_6_7.migrate(database)

        val columns = database.query("PRAGMA table_info(`orders`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }
        assertTrue(
            columns.containsAll(
                setOf(
                    "recognitionMode",
                    "recognitionInputType",
                    "recognitionTrigger",
                    "recognitionProvider",
                    "recognitionModel",
                    "recognitionUsedOfflineFallback",
                    "recognitionError",
                    "recognitionErrorDetail",
                    "recognitionDurationMs",
                ),
            ),
        )
    }

    @Test
    fun migrationSevenToEightAddsOcrDiagnosticDataColumn() {
        val database = openHelper.writableDatabase

        OrderDatabase.MIGRATION_6_7.migrate(database)
        OrderDatabase.MIGRATION_7_8.migrate(database)

        val columns = database.query("PRAGMA table_info(`orders`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }
        assertTrue(columns.contains("ocrDiagnosticData"))
    }

    private companion object {
        const val DATABASE_NAME = "migration-v6-v7-test.db"
    }
}
