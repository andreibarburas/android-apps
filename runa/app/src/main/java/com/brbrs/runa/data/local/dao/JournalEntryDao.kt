package com.brbrs.runa.data.local.dao

import androidx.room.*
import com.brbrs.runa.data.local.entity.JournalEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalEntryDao {

    @Query("SELECT * FROM journal_entries WHERE deleted = 0 ORDER BY entryDateTimeMs DESC")
    fun getAllEntries(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE deleted = 0 AND (title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%') ORDER BY entryDateTimeMs DESC")
    fun searchEntries(query: String): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id AND deleted = 0")
    suspend fun getById(id: String): JournalEntryEntity?

    @Upsert
    suspend fun upsert(entry: JournalEntryEntity)

    @Query("UPDATE journal_entries SET deleted = 1, updatedAtMs = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM journal_entries WHERE isSynced = 0 AND deleted = 0")
    suspend fun getUnsynced(): List<JournalEntryEntity>

    @Query("UPDATE journal_entries SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE journal_entries SET isSynced = 0 WHERE deleted = 0")
    suspend fun markAllUnsynced()

    @Query("SELECT * FROM journal_entries WHERE deleted = 0 AND latitude IS NOT NULL AND longitude IS NOT NULL ORDER BY entryDateTimeMs DESC")
    fun getEntriesWithLocation(): kotlinx.coroutines.flow.Flow<List<JournalEntryEntity>>

    @Query("SELECT COUNT(*) FROM journal_entries WHERE deleted = 0")
    fun getEntryCount(): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT id FROM journal_entries WHERE deleted = 1")
    suspend fun getDeletedIds(): List<String>

    @Query("SELECT * FROM journal_entries WHERE deleted = 0")
    suspend fun getAllForExport(): List<JournalEntryEntity>
}
