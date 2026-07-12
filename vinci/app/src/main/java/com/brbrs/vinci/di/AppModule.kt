package com.brbrs.vinci.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.brbrs.vinci.data.AttachmentDao
import com.brbrs.vinci.data.CallLogDao
import com.brbrs.vinci.data.ContactDao
import com.brbrs.vinci.data.VinciDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

// ── Room migrations ───────────────────────────────────────────────────────────
// Each migration adds only what changed — existing data is preserved.

// v1 → v2: added allPhones, allEmails, birthday, address columns to contacts
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE contacts ADD COLUMN allPhones TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE contacts ADD COLUMN allEmails TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE contacts ADD COLUMN birthday TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE contacts ADD COLUMN address TEXT NOT NULL DEFAULT ''")
    }
}

// v2 → v3: added isStarred to contacts
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE contacts ADD COLUMN isStarred INTEGER NOT NULL DEFAULT 0")
    }
}

// v3 → v4: added interactionType to call_logs
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE call_logs ADD COLUMN interactionType TEXT NOT NULL DEFAULT 'Call'")
    }
}

// v4 → v5: DAO-only changes (deleteLog, updateLog, getLogById) — no schema change
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // No schema changes — version bumped for DAO additions only
    }
}

// v5 → v6: contactId becomes nullable (unknown-number interactions), add normalizedPhone + tags.
// SQLite cannot ALTER a column's nullability, so recreate the table.
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE call_logs_new (
                id INTEGER NOT NULL PRIMARY KEY,
                contactId INTEGER,
                contactUid TEXT NOT NULL,
                contactName TEXT NOT NULL,
                phoneNumber TEXT NOT NULL,
                normalizedPhone TEXT NOT NULL,
                callTimestamp INTEGER NOT NULL,
                durationSeconds INTEGER NOT NULL,
                isOutgoing INTEGER NOT NULL,
                interactionType TEXT NOT NULL,
                reason TEXT NOT NULL,
                outcome TEXT NOT NULL,
                notes TEXT NOT NULL,
                tags TEXT NOT NULL,
                followUpDays INTEGER NOT NULL,
                isSynced INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO call_logs_new
                (id, contactId, contactUid, contactName, phoneNumber, normalizedPhone,
                 callTimestamp, durationSeconds, isOutgoing, interactionType, reason,
                 outcome, notes, tags, followUpDays, isSynced)
            SELECT id, contactId, contactUid, contactName, phoneNumber,
                   REPLACE(REPLACE(REPLACE(REPLACE(phoneNumber, ' ', ''), '-', ''), '(', ''), ')', ''),
                   callTimestamp, durationSeconds, isOutgoing, interactionType, reason,
                   outcome, notes, '', followUpDays, isSynced
            FROM call_logs
        """.trimIndent())
        db.execSQL("DROP TABLE call_logs")
        db.execSQL("ALTER TABLE call_logs_new RENAME TO call_logs")
    }
}

// v6 → v7: add attachments table for per-interaction file attachments.
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS attachments (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                logId INTEGER NOT NULL,
                fileName TEXT NOT NULL,
                mimeType TEXT NOT NULL,
                sizeBytes INTEGER NOT NULL,
                nextcloudPath TEXT NOT NULL,
                localPath TEXT NOT NULL,
                cachedLocally INTEGER NOT NULL,
                isSynced INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

// v7 → v8: add participants to call_logs for multi-contact interactions (e.g. group calls).
private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE call_logs ADD COLUMN participants TEXT NOT NULL DEFAULT '[]'")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VinciDatabase =
        Room.databaseBuilder(context, VinciDatabase::class.java, "vinci.db")
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
            )
            .build()

    @Provides @Singleton
    fun provideContactDao(db: VinciDatabase): ContactDao = db.contactDao()

    @Provides @Singleton
    fun provideCallLogDao(db: VinciDatabase): CallLogDao = db.callLogDao()

    @Provides @Singleton
    fun provideAttachmentDao(db: VinciDatabase): AttachmentDao = db.attachmentDao()

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val dnsCache = ConcurrentHashMap<String, List<InetAddress>>()
        val cachingDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                return dnsCache.getOrPut(hostname) { Dns.SYSTEM.lookup(hostname) }
            }
        }

        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .dns(cachingDns)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Vinci/1.5.3 (Android)")
                        .build()
                )
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .build()
    }
}
