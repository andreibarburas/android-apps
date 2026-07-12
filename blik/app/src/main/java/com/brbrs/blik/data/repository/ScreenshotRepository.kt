/*
 * Blik — a Nextcloud screenshot manager for Android
 * Copyright (C) 2026 andrei BARBURAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version. See <https://www.gnu.org/licenses/>.
 */

package com.brbrs.blik.data.repository

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.brbrs.blik.auth.AuthManager
import com.brbrs.blik.data.local.ScreenshotDao
import com.brbrs.blik.data.local.ScreenshotEntity
import com.brbrs.blik.data.local.UploadStatus
import com.brbrs.blik.network.AiClient
import com.brbrs.blik.network.WebDavClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenshotRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ScreenshotDao,
    private val webDavClient: WebDavClient,
    private val aiClient: AiClient,
    private val authManager: AuthManager,
    private val settingsRepo: SettingsRepository,
) {
    fun observeAll(): Flow<List<ScreenshotEntity>> = dao.observeAll()
    fun observePending(): Flow<List<ScreenshotEntity>> = dao.observePending()
    fun observeUploaded(): Flow<List<ScreenshotEntity>> = dao.observeUploaded()
    fun observeWithNotes(): Flow<List<ScreenshotEntity>> = dao.observeWithNotes()
    fun observeStarred(): Flow<List<ScreenshotEntity>> = dao.observeStarred()
    fun search(q: String): Flow<List<ScreenshotEntity>> = dao.search(q)
    fun filterByCategory(cat: String): Flow<List<ScreenshotEntity>> = dao.filterByCategory(cat)

    suspend fun getAllCategories(): List<String> = withContext(Dispatchers.IO) {
        dao.getAllCategories()
    }

    /**
     * Scan the folder selected via SAF (stored as a content:// tree URI).
     * - Adds new files found on disk.
     * - Reconciles existing records: marks files deleted externally as missing,
     *   or removes them entirely if they have no Nextcloud copy.
     */
    suspend fun scanFolder(folderUriString: String) = withContext(Dispatchers.IO) {
        val treeUri = Uri.parse(folderUriString)
        val dir = DocumentFile.fromTreeUri(context, treeUri)
        if (dir == null || !dir.isDirectory) {
            android.util.Log.e("BlikScan", "Not a valid directory: $folderUriString")
            return@withContext
        }

        val imageExtensions = setOf("png", "jpg", "jpeg", "webp")
        val files = dir.listFiles()
            .filter { it.isFile && it.name?.substringAfterLast('.')?.lowercase() in imageExtensions }
            .sortedByDescending { it.lastModified() }

        // Build set of URIs currently on disk
        val diskUris = files.map { it.uri.toString() }.toSet()

        // ── Reconcile existing DB records ────────────────────────────────────
        val allRecords = dao.observeAll().firstOrNull() ?: emptyList()
        for (record in allRecords) {
            val stillExists = try {
                // A content URI is valid if we can open an input stream for it
                context.contentResolver.openInputStream(Uri.parse(record.localPath))?.use { true } ?: false
            } catch (e: Exception) { false }

            when {
                stillExists && record.isLocalFileMissing -> {
                    // File came back (e.g. restored) — clear the missing flag
                    dao.update(record.copy(isLocalFileMissing = false))
                }
                !stillExists && !record.isLocalFileMissing -> {
                    if (record.uploadStatus == UploadStatus.UPLOADED && record.remotePath.isNotBlank()) {
                        // On Nextcloud — keep record but mark local file as missing
                        dao.update(record.copy(isLocalFileMissing = true))
                    } else {
                        // Not on Nextcloud and gone from phone — remove entirely
                        dao.deleteByPath(record.localPath)
                    }
                }
            }
        }

        // ── Add new files found on disk ──────────────────────────────────────
        for (docFile in files) {
            val contentUri = docFile.uri
            val uriString  = contentUri.toString()

            if (dao.getByPath(uriString) != null) continue

            val fileName = docFile.name ?: uriString.substringAfterLast('/')
            val hash = try { sha256FromUri(contentUri) } catch (e: Exception) { continue }
            val byHash = dao.getByHash(hash)

            dao.insertIfNew(ScreenshotEntity(
                localPath     = uriString,
                fileName      = fileName,
                fileHash      = hash,
                fileSizeBytes = docFile.length(),
                capturedAt    = docFile.lastModified(),
                uploadStatus  = if (byHash?.uploadStatus == UploadStatus.UPLOADED)
                    UploadStatus.UPLOADED else UploadStatus.PENDING,
                remoteFolder  = byHash?.remoteFolder ?: "",
                remotePath    = byHash?.remotePath ?: "",
            ))
        }
    }

    // Keep the old names so GalleryViewModel compiles without changes
    suspend fun scanLocalFolder(folderPath: String) = scanFolder(folderPath)
    suspend fun scanMediaStore(folderPath: String) { /* merged into scanFolder */ }

    /** Upload a single screenshot. localPath is now a content URI string. */
    suspend fun uploadScreenshot(
        entity: ScreenshotEntity,
        onConflict: String = "ASK",
    ): Result<ScreenshotEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val creds = authManager.credentials.firstOrNull()
                ?: throw Exception("Not logged in to Nextcloud")

            val baseRemoteFolder = settingsRepo.remoteFolder.firstOrNull() ?: "/Screenshots"
            val claudeKey  = settingsRepo.claudeApiKey.firstOrNull() ?: ""
            val openAiKey  = settingsRepo.openAiApiKey.firstOrNull() ?: ""
            val aiModel    = settingsRepo.aiModel.firstOrNull() ?: "CLAUDE"
            val autoCat    = settingsRepo.autoCategorize.firstOrNull() ?: false
            val autoDesc   = settingsRepo.autoAiDesc.firstOrNull() ?: false

            // Copy content URI to a temp file so OkHttp can stream it
            val tempFile = copyToTempFile(Uri.parse(entity.localPath), entity.fileName)
                ?: throw Exception("Cannot read file: ${entity.fileName}")

            try {
                dao.update(entity.copy(uploadStatus = UploadStatus.UPLOADING))

                var category    = entity.category
                var description = entity.aiDescription
                var tags        = entity.tags

                if ((autoCat || autoDesc) && (claudeKey.isNotBlank() || openAiKey.isNotBlank())) {
                    val aiResult = if (aiModel == "CLAUDE" && claudeKey.isNotBlank())
                        aiClient.analyseWithClaude(tempFile, claudeKey).getOrNull()
                    else if (openAiKey.isNotBlank())
                        aiClient.analyseWithOpenAI(tempFile, openAiKey).getOrNull()
                    else null

                    aiResult?.let {
                        if (autoCat)  category    = it.category
                        if (autoDesc) description = it.description
                        if (tags.isBlank()) tags  = it.tags.joinToString(",")
                    }
                }

                val remoteFolder = if (category.isNotBlank())
                    "${baseRemoteFolder.trimEnd('/')}/$category"
                else baseRemoteFolder

                webDavClient.ensureFolder(creds, remoteFolder)

                val remotePath = "${remoteFolder.trimEnd('/')}/${entity.fileName}"
                if (onConflict == "SKIP" && webDavClient.exists(creds, remotePath)) {
                    val updated = entity.copy(
                        uploadStatus  = UploadStatus.UPLOADED,
                        remoteFolder  = remoteFolder,
                        remotePath    = remotePath,
                        category      = category,
                        aiDescription = description,
                        tags          = tags,
                        lastAttemptAt = System.currentTimeMillis(),
                    )
                    dao.update(updated)
                    return@runCatching updated
                }

                val finalPath = webDavClient.upload(creds, tempFile, remoteFolder, entity.fileName)

                val mdContent = buildMarkdown(entity.fileName, description, entity.note, tags, category)
                if (mdContent.isNotBlank()) {
                    val mdName = entity.fileName.substringBeforeLast(".") + ".md"
                    try { webDavClient.uploadMarkdown(creds, mdContent, remoteFolder, mdName) }
                    catch (e: Exception) { /* non-fatal */ }
                }

                val updated = entity.copy(
                    uploadStatus   = UploadStatus.UPLOADED,
                    remoteFolder   = remoteFolder,
                    remotePath     = finalPath,
                    category       = category,
                    aiDescription  = description,
                    tags           = tags,
                    uploadErrorMsg = "",
                    lastAttemptAt  = System.currentTimeMillis(),
                )
                dao.update(updated)
                updated
            } finally {
                tempFile.delete()
            }
        }.onFailure { e ->
            dao.update(entity.copy(
                uploadStatus   = UploadStatus.ERROR,
                uploadErrorMsg = e.message ?: "Unknown error",
                lastAttemptAt  = System.currentTimeMillis(),
            ))
        }
    }

    /** Run AI analysis on-demand. */
    suspend fun runAiAnalysis(entity: ScreenshotEntity): Result<ScreenshotEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val claudeKey = settingsRepo.claudeApiKey.firstOrNull() ?: ""
                val openAiKey = settingsRepo.openAiApiKey.firstOrNull() ?: ""
                val aiModel   = settingsRepo.aiModel.firstOrNull() ?: "CLAUDE"

                val tempFile = copyToTempFile(Uri.parse(entity.localPath), entity.fileName)
                    ?: throw Exception("Cannot read file: ${entity.fileName}")

                try {
                    val result = if (aiModel == "CLAUDE" && claudeKey.isNotBlank())
                        aiClient.analyseWithClaude(tempFile, claudeKey).getOrThrow()
                    else if (openAiKey.isNotBlank())
                        aiClient.analyseWithOpenAI(tempFile, openAiKey).getOrThrow()
                    else
                        throw Exception("No AI API key configured. Add one in Settings.")

                    val updated = entity.copy(
                        aiDescription = result.description,
                        category      = result.category,
                        tags          = if (entity.tags.isBlank()) result.tags.joinToString(",") else entity.tags,
                    )
                    dao.update(updated)
                    updated
                } finally {
                    tempFile.delete()
                }
            }
        }

    suspend fun updateNote(localPath: String, note: String) = withContext(Dispatchers.IO) {
        val entity = dao.getByPath(localPath) ?: return@withContext
        dao.update(entity.copy(note = note))
        if (entity.uploadStatus == UploadStatus.UPLOADED) {
            val creds = authManager.credentials.firstOrNull() ?: return@withContext
            val mdContent = buildMarkdown(entity.fileName, entity.aiDescription, note, entity.tags, entity.category)
            val mdName = entity.fileName.substringBeforeLast(".") + ".md"
            try { webDavClient.uploadMarkdown(creds, mdContent, entity.remoteFolder, mdName) }
            catch (e: Exception) { /* non-fatal */ }
        }
    }

    suspend fun updateTags(localPath: String, tags: String) = withContext(Dispatchers.IO) {
        val entity = dao.getByPath(localPath) ?: return@withContext
        dao.update(entity.copy(tags = tags))
    }

    suspend fun toggleBlur(entity: ScreenshotEntity) = withContext(Dispatchers.IO) {
        dao.update(entity.copy(isBlurred = !entity.isBlurred))
    }

    suspend fun toggleStar(entity: ScreenshotEntity) = withContext(Dispatchers.IO) {
        dao.update(entity.copy(isStarred = !entity.isStarred))
    }

    suspend fun toggleStarTo(entity: ScreenshotEntity, starred: Boolean) = withContext(Dispatchers.IO) {
        dao.update(entity.copy(isStarred = starred))
    }

    suspend fun toggleBlurSelected(paths: Set<String>, blur: Boolean) = withContext(Dispatchers.IO) {
        val all = dao.observeAll().firstOrNull() ?: return@withContext
        all.filter { it.localPath in paths }.forEach { dao.update(it.copy(isBlurred = blur)) }
    }

    /** Delete the local file + the Blik record. Nextcloud copy untouched.
     *  Returns an IntentSender if a system confirmation dialog is required (Android 10+),
     *  null if the file was deleted immediately (Android 9-) or already missing. */
    suspend fun deleteLocalFile(entity: ScreenshotEntity): android.content.IntentSender? =
        withContext(Dispatchers.IO) {
            val mediaUri = resolveMediaStoreUri(Uri.parse(entity.localPath))
            if (mediaUri == null) {
                // Can't find it in MediaStore — just remove the record
                dao.deleteByPath(entity.localPath)
                return@withContext null
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                // Android 11+: createDeleteRequest shows system dialog; caller must launch it
                // and call deleteRecord() on success
                val pendingIntent = MediaStore.createDeleteRequest(
                    context.contentResolver, listOf(mediaUri)
                )
                pendingIntent.intentSender
            } else {
                // Android 9/10: direct delete works with WRITE_EXTERNAL_STORAGE / ownership
                try {
                    context.contentResolver.delete(mediaUri, null, null)
                } catch (e: Exception) {
                    android.util.Log.w("BlikRepo", "deleteLocalFile: ${e.message}")
                }
                dao.deleteByPath(entity.localPath)
                null
            }
        }

    /** Delete multiple local files. Returns an IntentSender for bulk system confirmation
     *  on Android 11+, or null if all were handled immediately. */
    suspend fun deleteLocalFiles(entities: List<ScreenshotEntity>): android.content.IntentSender? =
        withContext(Dispatchers.IO) {
            val mediaUris = entities.mapNotNull { resolveMediaStoreUri(Uri.parse(it.localPath)) }
            if (mediaUris.isEmpty()) {
                entities.forEach { dao.deleteByPath(it.localPath) }
                return@withContext null
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                MediaStore.createDeleteRequest(context.contentResolver, mediaUris).intentSender
            } else {
                mediaUris.forEach { uri ->
                    try { context.contentResolver.delete(uri, null, null) }
                    catch (e: Exception) { android.util.Log.w("BlikRepo", "deleteLocalFiles: ${e.message}") }
                }
                entities.forEach { dao.deleteByPath(it.localPath) }
                null
            }
        }

    /** Remove only the Room record (call after the system delete dialog confirms). */
    suspend fun deleteRecord(entity: ScreenshotEntity) = withContext(Dispatchers.IO) {
        dao.deleteByPath(entity.localPath)
    }

    /** Remove Room records for multiple entities (call after bulk system delete dialog confirms). */
    suspend fun deleteRecords(entities: List<ScreenshotEntity>) = withContext(Dispatchers.IO) {
        entities.forEach { dao.deleteByPath(it.localPath) }
    }

    /**
     * Resolves a SAF document URI to its MediaStore URI by querying via display name.
     * SAF URIs encode the path as "primary:DCIM/Screenshots/file.jpg" in the document ID.
     */
    private fun resolveMediaStoreUri(safUri: Uri): Uri? {
        // Extract filename from the SAF URI
        val docId = try {
            android.provider.DocumentsContract.getDocumentId(safUri)
        } catch (e: Exception) { safUri.lastPathSegment }

        val fileName = docId?.substringAfterLast("/")?.substringAfterLast(":")
            ?: safUri.lastPathSegment?.substringAfterLast("/")
            ?: return null

        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection  = "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, selection, arrayOf(fileName), null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                return android.content.ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )
            }
        }
        return null
    }

    /** Delete from Nextcloud (image + .md sidecar). Local file and record kept, status reset to PENDING. */
    suspend fun deleteFromNextcloud(entity: ScreenshotEntity) = withContext(Dispatchers.IO) {
        val creds = authManager.credentials.firstOrNull() ?: return@withContext
        // Delete image
        try { webDavClient.deleteFile(creds, entity.remotePath) }
        catch (e: Exception) { android.util.Log.w("BlikRepo", "deleteRemote image: ${e.message}") }
        // Delete .md sidecar if present
        val mdPath = entity.remotePath.substringBeforeLast(".") + ".md"
        try { webDavClient.deleteFile(creds, mdPath) }
        catch (e: Exception) { /* sidecar may not exist */ }
        // Reset upload status so it shows as pending again
        dao.update(entity.copy(uploadStatus = UploadStatus.PENDING, remotePath = "", remoteFolder = ""))
    }

    /** Delete local file + Nextcloud copy + Blik record.
     *  Returns IntentSender if system confirmation is needed (Android 11+). */
    suspend fun deleteEverywhere(entity: ScreenshotEntity): android.content.IntentSender? =
        withContext(Dispatchers.IO) {
            deleteFromNextcloud(entity)
            deleteLocalFile(entity)
        }

    // Legacy alias used by multi-select delete — deletes local file + record
    suspend fun delete(entity: ScreenshotEntity): android.content.IntentSender? = deleteLocalFile(entity)

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Copy a content URI to a temp file in the app's cache dir so OkHttp
     * and the AI clients (which expect a File) can read it.
     */
    private fun copyToTempFile(uri: Uri, fileName: String): File? {
        return try {
            val ext  = fileName.substringAfterLast('.', "png")
            val temp = File(context.cacheDir, "blik_upload_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            temp
        } catch (e: Exception) {
            android.util.Log.e("BlikRepo", "copyToTempFile failed: ${e.message}")
            null
        }
    }

    /** SHA-256 hash computed directly from a content URI stream. */
    private fun sha256FromUri(uri: Uri): String {
        val md = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.buffered(8192)?.use { stream ->
            val buf = ByteArray(8192)
            var n: Int
            while (stream.read(buf).also { n = it } != -1) md.update(buf, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(8192).use { stream ->
            val buf = ByteArray(8192)
            var n: Int
            while (stream.read(buf).also { n = it } != -1) md.update(buf, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun buildMarkdown(
        fileName: String, description: String,
        note: String, tags: String, category: String,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("# ${fileName.substringBeforeLast(".")}")
        sb.appendLine()
        if (category.isNotBlank()) sb.appendLine("**Category:** $category\n")
        if (description.isNotBlank()) { sb.appendLine("## AI Description"); sb.appendLine(description); sb.appendLine() }
        if (note.isNotBlank())        { sb.appendLine("## Note"); sb.appendLine(note); sb.appendLine() }
        if (tags.isNotBlank()) {
            val tagList = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
            sb.appendLine("## Tags")
            sb.appendLine(tagList.joinToString(" ") { "#$it" })
        }
        return sb.toString().trim()
    }
}
