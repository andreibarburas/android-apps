package com.brbrs.qarib.data.remote

import com.brbrs.qarib.auth.QaribSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder

/**
 * Minimal WebDAV client for syncing Qarib's places data and display
 * preferences as JSON files under the user's Nextcloud "Qarib" app folder
 * (or whichever folder name is stored in [QaribSession.qaribFolder]),
 * mirroring the approach used by Vinci's WebDavRepository.
 *
 * File layout on the server:
 *   /remote.php/dav/files/{username}/{qaribFolder}/places.json
 *   /remote.php/dav/files/{username}/{qaribFolder}/preferences.json
 */
class NextcloudWebDavClient(
    private val httpClient: OkHttpClient
) {
    sealed class Result<out T> {
        data class Success<T>(val data: T) : Result<T>()
        data class Error(val message: String) : Result<Nothing>()
        object NotFound : Result<Nothing>()
    }

    companion object {
        const val PLACES_FILE = "places.json"
        const val PREFERENCES_FILE = "preferences.json"
        const val PHOTOS_FOLDER = "photos"
    }

    private fun authedClient(session: QaribSession): OkHttpClient = httpClient.newBuilder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Authorization", session.basicAuthHeader())
                    .build()
            )
        }
        .build()

    private fun davBase(session: QaribSession): String {
        val folder = URLEncoder.encode(session.qaribFolder, "UTF-8").replace("+", "%20")
        return "${session.serverUrl.trimEnd('/')}/remote.php/dav/files/${session.username}/$folder"
    }

    /**
     * Ensures the app folder exists on the server, creating it with
     * MKCOL if necessary. Safe to call before every sync.
     */
    suspend fun ensureAppFolder(session: QaribSession): Result<Unit> = withContext(Dispatchers.IO) {
        val client = authedClient(session)
        val request = Request.Builder()
            .url(davBase(session))
            .method("MKCOL", null)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                // 201 = created, 405 = already exists — both are fine.
                if (response.code == 201 || response.code == 405) {
                    Result.Success(Unit)
                } else {
                    Result.Error("Could not create app folder (${response.code})")
                }
            }
        } catch (e: IOException) {
            Result.Error(e.message ?: "Network error")
        }
    }

    /**
     * Downloads a JSON file from the app folder. Returns [Result.NotFound]
     * if the file doesn't exist yet (e.g. first sync from a fresh account).
     */
    suspend fun downloadJson(session: QaribSession, fileName: String): Result<String> = withContext(Dispatchers.IO) {
        val client = authedClient(session)
        val request = Request.Builder()
            .url("${davBase(session)}/$fileName")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> Result.Success(response.body?.string().orEmpty())
                    response.code == 404 -> Result.NotFound
                    else -> Result.Error("Server returned ${response.code}")
                }
            }
        } catch (e: IOException) {
            Result.Error(e.message ?: "Network error")
        }
    }

    /**
     * Uploads JSON content to the given file in the app folder, overwriting
     * any existing file.
     */
    suspend fun uploadJson(session: QaribSession, fileName: String, json: String): Result<Unit> = withContext(Dispatchers.IO) {
        val client = authedClient(session)
        val body = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${davBase(session)}/$fileName")
            .put(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.Success(Unit)
                } else {
                    Result.Error("Server returned ${response.code}")
                }
            }
        } catch (e: IOException) {
            Result.Error(e.message ?: "Network error")
        }
    }

    /**
     * Ensures the Qarib/photos/ subfolder exists. Safe to call before
     * every photo upload.
     */
    suspend fun ensurePhotosFolder(session: QaribSession): Result<Unit> = withContext(Dispatchers.IO) {
        val client = authedClient(session)
        val request = Request.Builder()
            .url("${davBase(session)}/$PHOTOS_FOLDER")
            .method("MKCOL", null)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 201 || response.code == 405) {
                    Result.Success(Unit)
                } else {
                    Result.Error("Could not create photos folder (${response.code})")
                }
            }
        } catch (e: IOException) {
            Result.Error(e.message ?: "Network error")
        }
    }

    /** Downloads binary content (e.g. a photo) from Qarib/photos/{fileName}. */
    suspend fun downloadBytes(session: QaribSession, fileName: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        val client = authedClient(session)
        val request = Request.Builder()
            .url("${davBase(session)}/$PHOTOS_FOLDER/$fileName")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> Result.Success(response.body?.bytes() ?: ByteArray(0))
                    response.code == 404 -> Result.NotFound
                    else -> Result.Error("Server returned ${response.code}")
                }
            }
        } catch (e: IOException) {
            Result.Error(e.message ?: "Network error")
        }
    }

    /** Uploads binary content (e.g. a photo) to Qarib/photos/{fileName}, overwriting if present. */
    suspend fun uploadBytes(session: QaribSession, fileName: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        val client = authedClient(session)
        val body = bytes.toRequestBody("image/jpeg".toMediaType())
        val request = Request.Builder()
            .url("${davBase(session)}/$PHOTOS_FOLDER/$fileName")
            .put(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.Success(Unit)
                } else {
                    Result.Error("Server returned ${response.code}")
                }
            }
        } catch (e: IOException) {
            Result.Error(e.message ?: "Network error")
        }
    }

    /** Deletes a file from Qarib/photos/{fileName}, e.g. when a place's photo is removed. */
    suspend fun deletePhotoFile(session: QaribSession, fileName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val client = authedClient(session)
        val request = Request.Builder()
            .url("${davBase(session)}/$PHOTOS_FOLDER/$fileName")
            .delete()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                // 204/200 = deleted, 404 = already gone — both are fine.
                if (response.isSuccessful || response.code == 404) {
                    Result.Success(Unit)
                } else {
                    Result.Error("Server returned ${response.code}")
                }
            }
        } catch (e: IOException) {
            Result.Error(e.message ?: "Network error")
        }
    }
}
