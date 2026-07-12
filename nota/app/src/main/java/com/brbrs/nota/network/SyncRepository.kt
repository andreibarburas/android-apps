package com.brbrs.nota.network

import com.brbrs.nota.auth.AuthRepository
import com.brbrs.nota.auth.NotaSession
import com.brbrs.nota.data.NoteDao
import com.brbrs.nota.data.NoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncResult {
    object Success : SyncResult()
    data class Error(val message: String) : SyncResult()
    object Offline : SyncResult()
}

@Singleton
class SyncRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val noteDao: NoteDao,
    private val httpClient: OkHttpClient,
) {
    private fun authedClient(session: NotaSession) = httpClient.newBuilder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Authorization", session.basicAuthHeader())
                    .header("OCS-APIREQUEST", "true")
                    .header("Accept", "application/json")
                    .build()
            )
        }
        .build()

    private fun baseUrl(session: NotaSession) = session.serverUrl.trimEnd('/')

    // ── Full sync ─────────────────────────────────────────────────────────────

    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        val session = authRepository.session.first()
            ?: return@withContext SyncResult.Error("Not logged in")
        val client = authedClient(session)
        val base   = baseUrl(session)

        pushDirtyNotes(client, base)

        val request = Request.Builder()
            .url("$base/apps/notes/api/v1/notes")
            .get()
            .build()

        val body = runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext SyncResult.Error("HTTP ${resp.code}")
                resp.body!!.string()
            }
        }.getOrElse { return@withContext SyncResult.Offline }

        val arr = JSONArray(body)
        val remoteIds = (0 until arr.length()).map { i ->
            arr.getJSONObject(i).getLong("id")
        }.toSet()

        val entities = (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val id  = obj.getLong("id")
            val existing = noteDao.getNoteById(id)
            NoteEntity(
                id       = id,
                title    = obj.optString("title", ""),
                content  = obj.optString("content", ""),
                category = obj.optString("category", ""),
                modified = obj.optLong("modified", 0),
                favorite = obj.optBoolean("favorite", false),
                readonly = obj.optBoolean("readonly", false),
                etag     = obj.optString("etag", ""),
                isLocked = existing?.isLocked ?: false,
                isDirty  = false,
                isDeleted= false,
            )
        }
        noteDao.upsertAll(entities)

        // Remove local notes that were deleted on the server
        // (skip notes with isDirty=true — they have pending local changes)
        noteDao.getAllNotesSnapshot().forEach { local ->
            if (local.id > 0 && !local.isDirty && local.id !in remoteIds) {
                deleteNoteImages(client, base, local.content)
                noteDao.deleteNote(local.id)
            }
        }
        SyncResult.Success
    }

    // ── Push local changes ────────────────────────────────────────────────────

    private suspend fun pushDirtyNotes(client: OkHttpClient, base: String) {
        val json = "application/json".toMediaType()

        for (note in noteDao.getDeletedNotes()) {
            if (note.id > 0) {
                runCatching {
                    val req = Request.Builder()
                        .url("$base/apps/notes/api/v1/notes/${note.id}")
                        .delete()
                        .build()
                    client.newCall(req).execute().close()
                }
                // Also delete any images attached to this note
                deleteNoteImages(client, base, note.content)
            }
            noteDao.deleteNote(note.id)
        }

        for (note in noteDao.getDirtyNotes()) {
            val payload = JSONObject().apply {
                put("title",    note.title)
                put("content",  note.content)
                put("category", note.category)
                put("favorite", note.favorite)
            }.toString().toRequestBody(json)

            if (note.id < 0) {
                val result = runCatching {
                    val req = Request.Builder()
                        .url("$base/apps/notes/api/v1/notes")
                        .post(payload)
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            JSONObject(resp.body!!.string())
                        } else null
                    }
                }.getOrNull()
                if (result != null) {
                    noteDao.deleteNote(note.id)
                    noteDao.upsertNote(
                        note.copy(
                            id      = result.getLong("id"),
                            isDirty = false,
                            etag    = result.optString("etag", ""),
                        )
                    )
                }
            } else {
                val result = runCatching {
                    val req = Request.Builder()
                        .url("$base/apps/notes/api/v1/notes/${note.id}")
                        .put(payload)
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            JSONObject(resp.body!!.string())
                        } else null
                    }
                }.getOrNull()
                if (result != null) {
                    noteDao.markSynced(note.id, result.optString("etag", ""))
                }
            }
        }
    }

    private suspend fun deleteNoteImages(client: OkHttpClient, base: String, content: String) {
        val urls = com.brbrs.nota.ui.components.extractImageUrls(content)
        for (url in urls) {
            // Only delete files hosted on this Nextcloud instance
            if (!url.startsWith(base)) continue
            runCatching {
                val req = Request.Builder()
                    .url(url)
                    .delete()
                    .build()
                client.newCall(req).execute().close()
            }
        }
    }

    suspend fun createNoteLocal(title: String, content: String, category: String): Long {
        val localId = -(System.currentTimeMillis())
        noteDao.upsertNote(
            NoteEntity(
                id       = localId,
                title    = title,
                content  = content,
                category = category,
                modified = System.currentTimeMillis() / 1000,
                isDirty  = true,
            )
        )
        return localId
    }

    suspend fun updateNoteLocal(id: Long, title: String, content: String, category: String) {
        noteDao.updateNoteLocal(id, title, content, category, System.currentTimeMillis() / 1000)
    }

    suspend fun deleteNote(id: Long) {
        noteDao.markDeleted(id)
    }
}
