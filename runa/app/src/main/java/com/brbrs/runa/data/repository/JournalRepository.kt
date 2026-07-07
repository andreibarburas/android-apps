package com.brbrs.runa.data.repository

import com.brbrs.runa.data.local.dao.JournalEntryDao
import com.brbrs.runa.data.local.entity.JournalEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/** Domain model exposed to ViewModels. */
data class JournalEntry(
    val id: String,
    val title: String,
    val body: String,
    val entryDateTimeMs: Long,
    val createdAtMs: Long,
    val photoPaths: List<String>,
    val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Singleton
class JournalRepository @Inject constructor(
    private val dao: JournalEntryDao,
) {
    fun getAllEntries(): Flow<List<JournalEntry>> =
        dao.getAllEntries().map { list -> list.map { it.toDomain() } }

    fun searchEntries(query: String): Flow<List<JournalEntry>> =
        dao.searchEntries(query).map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): JournalEntry? = dao.getById(id)?.toDomain()

    suspend fun save(entry: JournalEntry) {
        dao.upsert(entry.toEntity())
    }

    suspend fun delete(id: String) {
        dao.softDelete(id)
    }

    // ── Sync helpers ──────────────────────────────────────────────────────────

    suspend fun getUnsynced(): List<JournalEntry> =
        dao.getUnsynced().map { it.toDomain() }

    suspend fun markSynced(id: String) = dao.markSynced(id)

    suspend fun markAllUnsynced() = dao.markAllUnsynced()

    fun getEntryCount(): kotlinx.coroutines.flow.Flow<Int> = dao.getEntryCount()

    suspend fun getDeletedIds(): List<String> = dao.getDeletedIds()

    suspend fun getAllForExport(): List<JournalEntry> = dao.getAllForExport().map { it.toDomain() }

    fun getEntriesWithLocation(): kotlinx.coroutines.flow.Flow<List<JournalEntry>> =
        dao.getEntriesWithLocation().map { list -> list.map { it.toDomain() } }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private fun JournalEntryEntity.toDomain() = JournalEntry(
        id              = id,
        title           = title,
        body            = body,
        entryDateTimeMs = entryDateTimeMs,
        createdAtMs     = createdAtMs,
        photoPaths      = parseJsonPaths(photoPathsJson),
        locationName    = locationName,
        latitude        = latitude,
        longitude       = longitude,
    )

    private fun JournalEntry.toEntity() = JournalEntryEntity(
        id              = id,
        title           = title,
        body            = body,
        entryDateTimeMs = entryDateTimeMs,
        createdAtMs     = createdAtMs,
        updatedAtMs     = System.currentTimeMillis(),
        photoPathsJson  = buildJsonPaths(photoPaths),
        locationName    = locationName,
        latitude        = latitude,
        longitude       = longitude,
    )

    private fun parseJsonPaths(json: String): List<String> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) { emptyList() }

    private fun buildJsonPaths(paths: List<String>): String {
        val arr = JSONArray()
        paths.forEach { arr.put(it) }
        return arr.toString()
    }
}
