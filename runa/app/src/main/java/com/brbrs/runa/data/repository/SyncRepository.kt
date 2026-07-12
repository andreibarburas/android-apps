package com.brbrs.runa.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.brbrs.runa.auth.AuthRepository
import com.brbrs.runa.auth.StorageMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncDataStore by preferencesDataStore(name = "runa_sync")

sealed class SyncState {
    object Idle    : SyncState()
    object Syncing : SyncState()
    data class Success(val timestampMs: Long) : SyncState()
    data class Error(val message: String)     : SyncState()
}

@Singleton
class SyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val journalRepository: JournalRepository,
    private val httpClient: OkHttpClient,
) {
    private val LAST_SYNCED_KEY = longPreferencesKey("last_synced_ms")

    val lastSyncedMs: Flow<Long?> = context.syncDataStore.data.map { prefs ->
        prefs[LAST_SYNCED_KEY]
    }

    val lastSyncedLabel: Flow<String> = lastSyncedMs.map { ms ->
        if (ms == null) "Never synced"
        else {
            val fmt = SimpleDateFormat("yyyy/MM/dd | HH:mm", Locale.getDefault())
            "Last synced ${fmt.format(Date(ms))}"
        }
    }

    /**
     * Marks every entry as unsynced, then runs a full sync.
     * Use this to force re-upload of all entries with the latest folder structure.
     */
    suspend fun resyncAll(): SyncState {
        journalRepository.markAllUnsynced()
        return sync()
    }

    /**
     * Full sync: for each unsynced entry, creates a date-based folder structure:
     *   Runa/YYYY/MM/DD/{entryId}.json
     *   Runa/YYYY/MM/DD/{entryId}-0.jpg
     *   Runa/YYYY/MM/DD/{entryId}-1.jpg  ...
     * No-op when running in local-only mode.
     */
    suspend fun sync(): SyncState {
        val mode    = authRepository.storageMode.first()
        val session = (mode as? StorageMode.Connected)?.session ?: return SyncState.Idle

        return try {
            val baseUrl   = session.serverUrl.trimEnd('/')
            val rootPath  = "/remote.php/dav/files/${session.username}/${session.runaFolder}"
            val rootUrl   = "$baseUrl$rootPath"
            val auth      = session.basicAuthHeader()

            withContext(Dispatchers.IO) {
                // Ensure top-level Runa folder exists
                ensureFolder(rootUrl, auth)

                // Delete remote files for locally-deleted entries
                val deletedIds = journalRepository.getDeletedIds()
                for (id in deletedIds) {
                    deleteEntryFromRemote(rootUrl, id, auth)
                }

                val unsynced = journalRepository.getUnsynced()
                for (entry in unsynced) {
                    // Build YYYY/MM/DD path from entry's own date
                    val cal      = java.util.Calendar.getInstance().apply { timeInMillis = entry.entryDateTimeMs }
                    val yyyy     = "%04d".format(cal.get(java.util.Calendar.YEAR))
                    val mm       = "%02d".format(cal.get(java.util.Calendar.MONTH) + 1)
                    val dd       = "%02d".format(cal.get(java.util.Calendar.DAY_OF_MONTH))

                    // Ensure each level of the date hierarchy exists
                    val yearUrl  = "$rootUrl/$yyyy"
                    val monthUrl = "$yearUrl/$mm"
                    val dayUrl   = "$monthUrl/$dd"
                    ensureFolder(yearUrl,  auth)
                    ensureFolder(monthUrl, auth)
                    ensureFolder(dayUrl,   auth)

                    // Upload JSON metadata
                    val json    = entryToJson(entry)
                    putText("$dayUrl/${entry.id}.json", json, auth)

                    // Upload each photo
                    entry.photoPaths.forEachIndexed { index, localPath ->
                        val photoFile = File(localPath)
                        if (photoFile.exists()) {
                            val ext      = photoFile.extension.ifBlank { "jpg" }
                            val photoUrl = "$dayUrl/${entry.id}-$index.$ext"
                            putBinary(photoUrl, photoFile, auth)
                        }
                    }

                    journalRepository.markSynced(entry.id)
                }
            }

            val now = System.currentTimeMillis()
            context.syncDataStore.edit { it[LAST_SYNCED_KEY] = now }
            SyncState.Success(now)
        } catch (e: Exception) {
            SyncState.Error(e.message ?: "Sync failed")
        }
    }

    // ── WebDAV helpers ────────────────────────────────────────────────────────

    /**
     * MKCOL — creates a collection (folder). 201 = created, 405 = already exists,
     * both are acceptable.
     */
    private fun ensureFolder(url: String, auth: String) {
        val request  = Request.Builder()
            .url(url)
            .method("MKCOL", null)
            .header("Authorization", auth)
            .build()
        val response = httpClient.newCall(request).execute()
        response.body?.close()
        // 201 Created or 405 Method Not Allowed (already exists) are both fine
        check(response.code == 201 || response.code == 405) {
            "MKCOL failed for $url: ${response.code}"
        }
    }

    /** PUT a text/JSON file. */
    private fun putText(url: String, content: String, auth: String) {
        val body     = content.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request  = Request.Builder()
            .url(url)
            .put(body)
            .header("Authorization", auth)
            .build()
        val response = httpClient.newCall(request).execute()
        check(response.isSuccessful) { "PUT failed for $url: ${response.code}" }
        response.body?.close()
    }

    /** PUT a binary file (photo). */
    private fun putBinary(url: String, file: File, auth: String) {
        val mediaType = when (file.extension.lowercase()) {
            "png"  -> "image/png"
            "webp" -> "image/webp"
            else   -> "image/jpeg"
        }.toMediaType()
        val body     = file.readBytes().toRequestBody(mediaType)
        val request  = Request.Builder()
            .url(url)
            .put(body)
            .header("Authorization", auth)
            .build()
        val response = httpClient.newCall(request).execute()
        check(response.isSuccessful) { "PUT photo failed for $url: ${response.code}" }
        response.body?.close()
    }

    /**
     * Searches for and deletes the remote day folder for a deleted entry.
     * Uses PROPFIND with Depth:4 to find any path containing the entry ID.
     * Best-effort — silently ignores all failures.
     */
    private fun deleteEntryFromRemote(rootUrl: String, entryId: String, auth: String) {
        try {
            val propfindBody = "<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/></d:prop></d:propfind>"
                .toRequestBody("application/xml".toMediaType())
            val propfind = Request.Builder()
                .url(rootUrl)
                .method("PROPFIND", propfindBody)
                .header("Authorization", auth)
                .header("Depth", "4")
                .build()
            val resp = httpClient.newCall(propfind).execute()
            val body = resp.body?.string() ?: return
            resp.body?.close()

            // Find href containing our entryId (could be .json or .jpg)
            val hrefRegex = Regex("<[Dd]:href>([^<]*/$entryId[^<]*)</[Dd]:href>")
            val match = hrefRegex.find(body) ?: return

            // Walk up to the day folder (YYYY/MM/DD level)
            val filePath  = match.groupValues[1]   // e.g. /remote.php/.../Runa/2026/06/27/id.json
            val dayFolder = filePath
                .substringBeforeLast("/")           // strip filename
                .trimEnd('/')
            if (dayFolder.isBlank()) return

            // Build absolute URL for the day folder
            val serverBase = rootUrl.substringBefore("/remote.php")
            val dayUrl = if (dayFolder.startsWith("http")) dayFolder else "$serverBase$dayFolder"

            deleteRemote(dayUrl, auth)
        } catch (_: Exception) { /* best-effort */ }
    }

    private fun deleteRemote(url: String, auth: String) {
        try {
            val req = Request.Builder().url(url).delete().header("Authorization", auth).build()
            httpClient.newCall(req).execute().body?.close()
        } catch (_: Exception) { /* ignore */ }
    }

    private fun entryToJson(entry: JournalEntry): String {
        val obj = JSONObject().apply {
            put("id",              entry.id)
            put("title",          entry.title)
            put("body",           entry.body)
            put("entryDateTimeMs", entry.entryDateTimeMs)
            put("createdAtMs",    entry.createdAtMs)
            put("photoCount",     entry.photoPaths.size)
            entry.locationName?.let { put("locationName", it) }
            entry.latitude?.let  { put("latitude",  it) }
            entry.longitude?.let { put("longitude", it) }
        }
        return obj.toString(2)
    }
}
