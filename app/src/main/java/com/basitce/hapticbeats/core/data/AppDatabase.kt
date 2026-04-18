package com.basitce.hapticbeats.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Song::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    companion object {
        private val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateSongsTable(db)
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateSongsTable(db)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_songs_title")
            }
        }

        private fun migrateSongsTable(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS songs_new")
            db.execSQL("DROP INDEX IF EXISTS index_songs_title")

            val existingColumns = db.query("PRAGMA table_info(songs)").use { cursor ->
                val nameColumnIndex = cursor.getColumnIndex("name")
                buildSet {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(nameColumnIndex))
                    }
                }
            }

            val fallbackAnalysisState = when {
                "analysisState" in existingColumns -> "analysisState"
                "vibrationPatternJson" in existingColumns ->
                    "CASE WHEN vibrationPatternJson IS NOT NULL AND TRIM(vibrationPatternJson) != '' THEN 'stale' ELSE 'missing' END"
                else -> "'missing'"
            }

            db.execSQL(
                """
                CREATE TABLE songs_new (
                    uri TEXT NOT NULL PRIMARY KEY,
                    mediaStoreId INTEGER,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    duration INTEGER NOT NULL,
                    albumArtUri TEXT,
                    dateAdded INTEGER NOT NULL,
                    dateModified INTEGER NOT NULL,
                    fileSize INTEGER NOT NULL,
                    analysisVersion INTEGER NOT NULL,
                    analysisState TEXT NOT NULL,
                    patternKey TEXT
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO songs_new (
                    uri,
                    mediaStoreId,
                    title,
                    artist,
                    duration,
                    albumArtUri,
                    dateAdded,
                    dateModified,
                    fileSize,
                    analysisVersion,
                    analysisState,
                    patternKey
                )
                SELECT
                    ${existingColumns.columnOrFallback("uri", "''")},
                    ${existingColumns.columnOrFallback("mediaStoreId", "NULL")},
                    ${existingColumns.columnOrFallback("title", "''")},
                    ${existingColumns.columnOrFallback("artist", "''")},
                    ${existingColumns.columnOrFallback("duration", "0")},
                    ${existingColumns.columnOrFallback("albumArtUri", "NULL")},
                    ${existingColumns.columnOrFallback("dateAdded", "0")},
                    ${existingColumns.columnOrFallback("dateModified", "0")},
                    ${existingColumns.columnOrFallback("fileSize", "0")},
                    ${existingColumns.columnOrFallback("analysisVersion", "0")},
                    $fallbackAnalysisState,
                    ${existingColumns.columnOrFallback("patternKey", "NULL")}
                FROM songs
                """.trimIndent()
            )

            db.execSQL("DROP TABLE songs")
            db.execSQL("ALTER TABLE songs_new RENAME TO songs")
        }

        private fun Set<String>.columnOrFallback(columnName: String, fallback: String): String {
            return if (columnName in this) columnName else fallback
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hapticbeats_database"
                )
                    .addMigrations(MIGRATION_1_3, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
