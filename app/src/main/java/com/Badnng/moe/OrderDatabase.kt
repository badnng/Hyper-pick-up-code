package com.Badnng.moe

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [OrderEntity::class, OrderGroup::class], version = 5, exportSchema = false)
abstract class OrderDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao
    abstract fun orderGroupDao(): OrderGroupDao

    companion object {
        @Volatile
        private var INSTANCE: OrderDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN fullText TEXT")
                db.execSQL("ALTER TABLE orders ADD COLUMN sourceApp TEXT")
                db.execSQL("ALTER TABLE orders ADD COLUMN sourcePackage TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN pickupLocation TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 第一步：先创建 order_groups 表（因为 orders 外键依赖它）
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS order_groups (
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
                        completedAt INTEGER
                    )
                """)

                // 第二步：创建新的 orders 表（带外键和索引）
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS orders_new (
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
                        FOREIGN KEY (groupId) REFERENCES order_groups(id) ON DELETE CASCADE
                    )
                """)

                // 第三步：从旧表复制数据到新表
                db.execSQL("""
                    INSERT INTO orders_new (id, takeoutCode, qrCodeData, screenshotPath, recognizedText, orderType, brandName, pickupLocation, fullText, sourceApp, sourcePackage, isCompleted, createdAt, completedAt, groupId)
                    SELECT id, takeoutCode, qrCodeData, screenshotPath, recognizedText, orderType, brandName, pickupLocation, fullText, sourceApp, sourcePackage, isCompleted, createdAt, completedAt, NULL
                    FROM orders
                """)

                // 第四步：删除旧表
                db.execSQL("DROP TABLE orders")

                // 第五步：重命名新表
                db.execSQL("ALTER TABLE orders_new RENAME TO orders")

                // 第六步：创建索引
                db.execSQL("CREATE INDEX IF NOT EXISTS index_orders_groupId ON orders(groupId)")
            }
        }

        fun getDatabase(context: Context): OrderDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OrderDatabase::class.java,
                    "order_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
