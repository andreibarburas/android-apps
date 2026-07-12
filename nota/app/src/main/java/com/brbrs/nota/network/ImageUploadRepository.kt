package com.brbrs.nota.network

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.brbrs.nota.auth.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import javax.inject.Inject
import javax.inject.Singleton

sealed class UploadResult {
    data class Success(val markdownLink: String) : UploadResult()
    data class Error(val message: String) : UploadResult()
}

@Singleton
class ImageUploadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val httpClient: OkHttpClient,
) {
    /**
     * Uploads [uri] to /Nota Attachments/ on Nextcloud via WebDAV
     * and returns a markdown image string pointing to the file.
     */
    suspend fun uploadImage(uri: Uri): UploadResult = withContext(Dispatchers.IO) {
        val session = authRepository.session.first()
            ?: return@withContext UploadResult.Error("Not logged in")

        // 1. Resolve filename and mime type
        val filename = resolveFilename(uri) ?: "image_${System.currentTimeMillis()}.jpg"
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"

        // 2. Read the image bytes
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext UploadResult.Error("Cannot read image")
        } catch (e: Exception) {
            return@withContext UploadResult.Error("Cannot read image: ${e.message}")
        }

        // 3. Ensure the upload folder exists (MKCOL is idempotent — 405 = already exists)
        val base      = session.serverUrl.trimEnd('/')
        val folderUrl = "$base/remote.php/dav/files/${session.username}/Nota%20Attachments"
        val client    = authedClient(session)

        runCatching {
            val mkcolReq = Request.Builder()
                .url(folderUrl)
                .method("MKCOL", null)
                .build()
            client.newCall(mkcolReq).execute().close()
        }

        // 4. Upload via WebDAV PUT
        val fileUrl = "$folderUrl/${Uri.encode(filename)}"
        val body    = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val putReq  = Request.Builder()
            .url(fileUrl)
            .put(body)
            .build()

        val resp = runCatching {
            client.newCall(putReq).execute()
        }.getOrElse {
            return@withContext UploadResult.Error("Upload failed: ${it.message}")
        }

        resp.close()
        if (!resp.isSuccessful && resp.code != 204) {
            return@withContext UploadResult.Error("Upload failed: HTTP ${resp.code}")
        }

        // 5. Build a direct download URL for the markdown image tag
        val downloadUrl = "$base/remote.php/dav/files/${session.username}/Nota%20Attachments/${Uri.encode(filename)}"
        UploadResult.Success("![$filename]($downloadUrl)")
    }

    private fun resolveFilename(uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            if (col >= 0) cursor.getString(col) else null
        }
    } catch (e: Exception) { null }
        ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun authedClient(session: com.brbrs.nota.auth.NotaSession) =
        httpClient.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", session.basicAuthHeader())
                        .build()
                )
            }
            .build()
}
