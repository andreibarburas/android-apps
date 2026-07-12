package com.brbrs.merk.network

import com.brbrs.merk.auth.AuthCredentials
import com.brbrs.merk.data.local.BookmarkEntity
import com.brbrs.merk.data.local.FolderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarksApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private fun baseUrl(creds: AuthCredentials) =
        "${creds.serverUrl}/index.php/apps/bookmarks/public/rest/v2"

    private fun authHeader(creds: AuthCredentials) =
        Credentials.basic(creds.username, creds.appPassword)

    private fun get(url: String, creds: AuthCredentials): JSONObject {
        val req  = Request.Builder().url(url).header("Authorization", authHeader(creds)).get().build()
        val resp = okHttpClient.newCall(req).execute()
        check(resp.isSuccessful) { "GET $url → HTTP ${resp.code}" }
        return JSONObject(resp.body!!.string())
    }

    private fun post(url: String, body: JSONObject, creds: AuthCredentials): JSONObject {
        val reqBody = body.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url).header("Authorization", authHeader(creds))
            .post(reqBody).build()
        val resp = okHttpClient.newCall(req).execute()
        check(resp.isSuccessful) { "POST $url → HTTP ${resp.code}" }
        return JSONObject(resp.body!!.string())
    }

    private fun put(url: String, body: JSONObject, creds: AuthCredentials): JSONObject {
        val reqBody = body.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url).header("Authorization", authHeader(creds))
            .put(reqBody).build()
        val resp = okHttpClient.newCall(req).execute()
        check(resp.isSuccessful) { "PUT $url → HTTP ${resp.code}" }
        return JSONObject(resp.body!!.string())
    }

    private fun delete(url: String, creds: AuthCredentials) {
        val req  = Request.Builder().url(url).header("Authorization", authHeader(creds)).delete().build()
        val resp = okHttpClient.newCall(req).execute()
        check(resp.isSuccessful) { "DELETE $url → HTTP ${resp.code}" }
    }

    suspend fun fetchAllBookmarks(creds: AuthCredentials): List<BookmarkEntity> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<BookmarkEntity>()
            var page = 0
            while (true) {
                val url = "${baseUrl(creds)}/bookmark?limit=100&page=$page&sortby=lastmodified&order=desc"
                val json = get(url, creds)
                val arr  = json.getJSONArray("data")
                if (arr.length() == 0) break
                for (i in 0 until arr.length()) result += arr.getJSONObject(i).toEntity()
                page++
            }
            result
        }

    suspend fun createBookmark(creds: AuthCredentials, entity: BookmarkEntity): BookmarkEntity =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("url",         entity.url)
                put("title",       entity.title)
                put("description", entity.description)
                put("tags",        JSONArray(entity.tags.split(",").filter { it.isNotBlank() }))
                if (entity.folderId > 0) put("folders", JSONArray().put(entity.folderId))
            }
            post("${baseUrl(creds)}/bookmark", body, creds).getJSONObject("item").toEntity()
        }

    suspend fun updateBookmark(creds: AuthCredentials, entity: BookmarkEntity): BookmarkEntity =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("url",         entity.url)
                put("title",       entity.title)
                put("description", entity.description)
                put("tags",        JSONArray(entity.tags.split(",").filter { it.isNotBlank() }))
            }
            put("${baseUrl(creds)}/bookmark/${entity.id}", body, creds).getJSONObject("item").toEntity()
        }

    suspend fun deleteBookmark(creds: AuthCredentials, id: Long) =
        withContext(Dispatchers.IO) {
            delete("${baseUrl(creds)}/bookmark/$id", creds)
        }

    suspend fun fetchFolders(creds: AuthCredentials): List<FolderEntity> =
        withContext(Dispatchers.IO) {
            val json = get("${baseUrl(creds)}/folder", creds)
            parseFolders(json.getJSONArray("data"))
        }

    suspend fun createFolder(creds: AuthCredentials, title: String, parentId: Long = -1): FolderEntity =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("title", title)
                if (parentId > 0) put("parent_folder", parentId)
            }
            val resp = post("${baseUrl(creds)}/folder", body, creds)
            val obj  = resp.getJSONObject("item")
            FolderEntity(
                id             = obj.getLong("id"),
                title          = obj.optString("title", title),
                parentFolderId = parentId,
            )
        }

    private fun parseFolders(arr: JSONArray, parentId: Long = -1): List<FolderEntity> {
        val result = mutableListOf<FolderEntity>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result += FolderEntity(
                id             = obj.getLong("id"),
                title          = obj.optString("title", ""),
                parentFolderId = parentId,
            )
            if (obj.has("children")) {
                result += parseFolders(obj.getJSONArray("children"), obj.getLong("id"))
            }
        }
        return result
    }

    private fun JSONObject.toEntity(): BookmarkEntity {
        val tags = buildString {
            val arr = optJSONArray("tags") ?: JSONArray()
            for (i in 0 until arr.length()) {
                if (i > 0) append(",")
                append(arr.getString(i))
            }
        }
        val folderId = optJSONArray("folders")?.optLong(0) ?: -1L
        return BookmarkEntity(
            id          = getLong("id"),
            url         = optString("url", ""),
            title       = optString("title", optString("url", "")),
            description = optString("description", ""),
            tags        = tags,
            folderId    = folderId,
            folderName  = "",
            faviconUrl  = optString("favicon", ""),
            addedAt     = optLong("added", System.currentTimeMillis() / 1000),
        )
    }
}
