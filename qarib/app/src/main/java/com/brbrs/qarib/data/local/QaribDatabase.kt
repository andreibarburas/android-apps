package com.brbrs.qarib.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.brbrs.qarib.data.local.dao.PlaceDao
import com.brbrs.qarib.data.local.dao.VisitDao
import com.brbrs.qarib.data.local.entity.PlaceEntity
import com.brbrs.qarib.data.local.entity.VisitEntity

@Database(
    entities = [PlaceEntity::class, VisitEntity::class],
    version = 6,
    exportSchema = true
)
abstract class QaribDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
    abstract fun visitDao(): VisitDao

    companion object {
        const val DATABASE_NAME = "qarib.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE places ADD COLUMN country TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE places ADD COLUMN visited INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE places ADD COLUMN notificationsMuted INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE places ADD COLUMN photoPath TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE places ADD COLUMN geofenceRadiusMeters INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE places ADD COLUMN snoozedUntil INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE places ADD COLUMN snoozedUntilExit INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Adds the visits table for per-place visit history.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS visits (
                        id TEXT NOT NULL PRIMARY KEY,
                        placeId TEXT NOT NULL,
                        visitedAt INTEGER NOT NULL,
                        note TEXT NOT NULL DEFAULT '',
                        photoPaths TEXT NOT NULL DEFAULT '[]',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deleted INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (placeId) REFERENCES places(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_visits_placeId ON visits(placeId)")
            }
        }
    }
}
