package com.borgorninja.androtask.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Macro::class, MacroStep::class, ScheduledTrigger::class],
    version  = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun macroDao(): MacroDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `scheduled_triggers` (
                        `id`         INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `macroId`    INTEGER NOT NULL,
                        `hourOfDay`  INTEGER NOT NULL,
                        `minute`     INTEGER NOT NULL,
                        `repeatDays` TEXT    NOT NULL,
                        `isEnabled`  INTEGER NOT NULL,
                        `label`      TEXT    NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "androtask.db")
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
