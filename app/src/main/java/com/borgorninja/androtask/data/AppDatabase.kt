package com.borgorninja.androtask.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities     = [Macro::class, MacroStep::class, ScheduledTrigger::class],
    version      = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun macroDao(): MacroDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // v1 → v2: added scheduled_triggers table
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `scheduled_triggers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `macroId` INTEGER NOT NULL,
                        `hourOfDay` INTEGER NOT NULL,
                        `minute` INTEGER NOT NULL,
                        `repeatDays` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `label` TEXT NOT NULL
                    )""".trimIndent())
            }
        }

        // v2 → v3: added columns to macros + macro_steps
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Macro additions
                db.execSQL("ALTER TABLE `macros` ADD COLUMN `speedMultiplier` REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE `macros` ADD COLUMN `recordedWidth`   INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `macros` ADD COLUMN `recordedHeight`  INTEGER NOT NULL DEFAULT 0")
                // MacroStep additions
                db.execSQL("ALTER TABLE `macro_steps` ADD COLUMN `text`   TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `macro_steps` ADD COLUMN `label`  TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `macro_steps` ADD COLUMN `jitter` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "androtask.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
