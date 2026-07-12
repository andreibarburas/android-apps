package com.brbrs.qarib.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.brbrs.qarib.data.local.dao.PlaceDao
import com.brbrs.qarib.data.local.entity.PlaceEntity

@Database(
    entities = [PlaceEntity::class],
    version = 5,
    exportSchema = true
)
abstract class QaribDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao

    companion object {
        const val DATABASE_NAME = "qarib.db"

        /**
         * Adds country/visited/notificationsMuted columns. Existing rows
         * get empty/false defaults; [com.brbrs.qarib.data.repository.PlacesRepository]
         * backfills `country` for rows where it's still empty by deriving
         * it from the stored `address` string on next load.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE places ADD COLUMN country TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE places ADD COLUMN visited INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE places ADD COLUMN notificationsMuted INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Adds the photoPath column for user-attached place photos.
         * Empty string means no photo attached.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE places ADD COLUMN photoPath TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * Adds the geofenceRadiusMeters column for per-place notification
         * radius overrides. NULL means "use the global default radius"
         * from DisplayPreferencesRepository.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE places ADD COLUMN geofenceRadiusMeters INTEGER DEFAULT NULL")
            }
        }

        /**
         * Adds snooze state for "nearby" notifications:
         * - snoozedUntil: epoch millis; suppress notifications for this
         *   place until this time has passed. NULL = not snoozed.
         * - snoozedUntilExit: suppress notifications for this place until
         *   the user exits its geofence, then auto-clears.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE places ADD COLUMN snoozedUntil INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE places ADD COLUMN snoozedUntilExit INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
