package com.brbrs.vinci.network

import com.brbrs.vinci.auth.AuthRepository
import com.brbrs.vinci.auth.VinciSession
import com.brbrs.vinci.data.CallLogDao
import com.brbrs.vinci.data.CallLogEntity
import com.brbrs.vinci.data.ContactDao
import com.brbrs.vinci.util.parseParticipants
import com.brbrs.vinci.util.stableLogId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val callLogDao: CallLogDao,
    private val contactDao: ContactDao,
    private val httpClient: OkHttpClient,
) {
    // -- Auth helpers --

    private fun authedClient(session: VinciSession) = httpClient.newBuilder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Authorization", session.basicAuthHeader())
                    .build()
            )
        }
        .build()

    private fun davBase(session: VinciSession): String {
        val folder = URLEncoder.encode(session.vinciFolder, "UTF-8")
        return "${session.serverUrl.trimEnd('/')}/remote.php/dav/files/${session.username}/$folder"
    }

    // -- Folder creation --

    suspend fun ensureFolderExists(session: VinciSession) = withContext(Dispatchers.IO) {
        val client = authedClient(session)
        listOf(davBase(session), "${davBase(session)}/Contacts").forEach { url ->
            runCatching {
                client.newCall(Request.Builder().url(url).method("MKCOL", null).build()).execute().close()
            }
        }
    }

    suspend fun ensureContactFolder(session: VinciSession, folderName: String) = withContext(Dispatchers.IO) {
        val client   = authedClient(session)
        val base     = davBase(session)
        val safeName = URLEncoder.encode(folderName, "UTF-8")
        listOf("$base/Contacts/$safeName", "$base/Contacts/$safeName/calls").forEach { url ->
            runCatching {
                client.newCall(Request.Builder().url(url).method("MKCOL", null).build()).execute().close()
            }
        }
    }

    // -- index.md - per-contact metadata (starred, followUp) --

    /**
     * Writes Vinci/Contacts/[folder]/index.md with starred and followUp state.
     * Called whenever isStarred or followUpDue changes for a contact.
     */
    suspend fun writeIndexMd(
        contactUid: String,
        contactName: String,
        isStarred: Boolean,
        followUpDue: Long,
    ) = withContext(Dispatchers.IO) {
        val session = authRepository.session.first() ?: return@withContext
        val client  = authedClient(session)
        val base    = davBase(session)

        val folderName = buildFolderName(contactName, contactUid)
        ensureContactFolder(session, folderName)

        val safeName = URLEncoder.encode(folderName, "UTF-8")
        val url      = "$base/Contacts/$safeName/index.md"

        val md = buildString {
            appendLine("# Vinci Contact Index")
            appendLine()
            appendLine("contact_uid: $contactUid")
            appendLine("contact_name: $contactName")
            appendLine("starred: $isStarred")
            appendLine("follow_up_due: $followUpDue")
            appendLine("updated: ${System.currentTimeMillis()}")
        }

        runCatching {
            client.newCall(
                Request.Builder()
                    .url(url)
                    .put(md.toRequestBody("text/markdown".toMediaType()))
                    .build()
            ).execute().close()
        }
    }

    // -- preferences.json - display preferences --

    suspend fun uploadPreferences(prefs: Map<String, Any>) = withContext(Dispatchers.IO) {
        val session = authRepository.session.first() ?: return@withContext
        val client  = authedClient(session)
        val url     = "${davBase(session)}/preferences.json"

        val json = JSONObject(prefs).toString()
        runCatching {
            client.newCall(
                Request.Builder()
                    .url(url)
                    .put(json.toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute().close()
        }
    }

    suspend fun downloadPreferences(): Map<String, Any>? = withContext(Dispatchers.IO) {
        val session = authRepository.session.first() ?: return@withContext null
        val client  = authedClient(session)
        val url     = "${davBase(session)}/preferences.json"

        return@withContext runCatching {
            val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
            if (!resp.isSuccessful) return@runCatching null
            val body = resp.body?.string() ?: return@runCatching null
            val json = JSONObject(body)
            val result = mutableMapOf<String, Any>()
            json.keys().forEach { key -> result[key] = json.get(key) }
            result as Map<String, Any>
        }.getOrNull()
    }

    // -- Call log upload --

    suspend fun uploadCallLog(log: CallLogEntity): Boolean = withContext(Dispatchers.IO) {
        val session = authRepository.session.first() ?: return@withContext false
        val client  = authedClient(session)
        val base    = davBase(session)

        val folderName = buildFolderName(log.contactName, log.contactUid)
        ensureContactFolder(session, folderName)

        val timestamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.getDefault()).format(Date(log.callTimestamp))
        val safeName  = URLEncoder.encode(folderName, "UTF-8")
        val url       = "$base/Contacts/$safeName/calls/$timestamp.md"

        return@withContext runCatching {
            val resp = client.newCall(
                Request.Builder()
                    .url(url)
                    .put(buildMarkdown(log).toRequestBody("text/markdown".toMediaType()))
                    .build()
            ).execute()
            resp.isSuccessful.also { resp.close() }
        }.getOrDefault(false)
    }

    /** Clears the cached CardDAV address book URL so it is re-discovered on next use. */
    suspend fun clearAddressBookUrlCache() {
        authRepository.saveAddressBookUrl("")
    }

    suspend fun deleteCallLog(log: CallLogEntity): Boolean = withContext(Dispatchers.IO) {
        val session = authRepository.session.first() ?: return@withContext false
        val client  = authedClient(session)
        val base    = davBase(session)

        val folderName = buildFolderName(log.contactName, log.contactUid)
        val timestamp  = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.getDefault()).format(Date(log.callTimestamp))
        val safeName   = URLEncoder.encode(folderName, "UTF-8")
        val url        = "$base/Contacts/$safeName/calls/$timestamp.md"

        return@withContext runCatching {
            val resp = client.newCall(
                Request.Builder().url(url).delete().build()
            ).execute()
            resp.close()
            true
        }.getOrDefault(false)
    }

    suspend fun syncPendingLogs() {
        val unsynced = callLogDao.getUnsynced()
        for (log in unsynced) {
            if (uploadCallLog(log)) callLogDao.markSynced(log.id)
        }
    }

    /**
     * One-time-per-sync cleanup for the duplicate rows created by a now-fixed bug: before
     * stableLogId() existed, a locally-created log's id (System.currentTimeMillis(), full
     * precision) never matched the id restoreFromNextcloud() would derive when re-parsing the
     * very same file (minute-truncated), so every locally-created interaction that got synced
     * and then re-imported ended up duplicated as two rows -- one file, two local entries.
     *
     * Groups by (contact, same-minute timestamp) rather than comparing text fields like notes --
     * an earlier version matched on reason/outcome/notes too, but long multi-line notes are
     * prone to whitespace differences between "as typed" and "as re-parsed from the file",
     * which silently defeated an exact-string match and let some duplicates slip through.
     * Within the same contact and the same minute, two genuinely distinct interactions are
     * very unlikely for personal use, so this is safe in practice.
     */
    suspend fun dedupeCallLogs() {
        val all = callLogDao.getAllLogsOnce()
        val groups = all.groupBy { Triple(it.contactId, it.normalizedPhone, it.callTimestamp / 60000) }
        for ((_, group) in groups) {
            if (group.size <= 1) continue
            // Prefer whichever row already has the correct, re-derivable id; otherwise keep the lowest id.
            val keep = group.firstOrNull { it.id == stableLogId(it.callTimestamp) } ?: group.minByOrNull { it.id }!!
            for (log in group) {
                if (log.id != keep.id) {
                    callLogDao.deleteLog(log.id)
                }
            }
        }
    }

    // -- Attachment upload/download --

    /**
     * Uploads raw [bytes] as an attachment for [log] to
     * Vinci/Contacts/[folder]/calls/[timestamp]-attachments/[fileName].
     * Returns the WebDAV path (relative to davBase) on success, or null on failure.
     */
    suspend fun uploadAttachment(log: CallLogEntity, fileName: String, mimeType: String, bytes: ByteArray): String? =
        withContext(Dispatchers.IO) {
            val session = authRepository.session.first() ?: return@withContext null
            val client  = authedClient(session)
            val base    = davBase(session)

            val folderName = buildFolderName(log.contactName, log.contactUid)
            ensureContactFolder(session, folderName)

            val timestamp  = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.getDefault()).format(Date(log.callTimestamp))
            val safeFolder = URLEncoder.encode(folderName, "UTF-8")
            val attachDir  = "$base/Contacts/$safeFolder/calls/$timestamp-attachments"

            runCatching {
                client.newCall(Request.Builder().url(attachDir).method("MKCOL", null).build()).execute().close()
            }

            val safeFile = URLEncoder.encode(fileName, "UTF-8")
            val url      = "$attachDir/$safeFile"
            val mediaType = (mimeType.ifBlank { "application/octet-stream" }).toMediaType()

            return@withContext runCatching {
                val resp = client.newCall(
                    Request.Builder().url(url).put(bytes.toRequestBody(mediaType)).build()
                ).execute()
                val ok = resp.isSuccessful
                resp.close()
                if (ok) "Contacts/$folderName/calls/$timestamp-attachments/$fileName" else null
            }.getOrNull()
        }

    /** Downloads an attachment by its [nextcloudPath] (as stored in [AttachmentEntity.nextcloudPath]) into [destFile]. */
    suspend fun downloadAttachment(nextcloudPath: String, destFile: java.io.File): Boolean = withContext(Dispatchers.IO) {
        val session = authRepository.session.first() ?: return@withContext false
        val client  = authedClient(session)
        val base    = davBase(session)
        val url     = "$base/" + nextcloudPath.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8") }

        return@withContext runCatching {
            val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
            if (!resp.isSuccessful) { resp.close(); return@runCatching false }
            resp.body!!.byteStream().use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            resp.close()
            true
        }.getOrDefault(false)
    }

    /** Deletes an attachment from Nextcloud by its [nextcloudPath]. */
    suspend fun deleteAttachment(nextcloudPath: String): Boolean = withContext(Dispatchers.IO) {
        val session = authRepository.session.first() ?: return@withContext false
        val client  = authedClient(session)
        val base    = davBase(session)
        val url     = "$base/" + nextcloudPath.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8") }

        return@withContext runCatching {
            val resp = client.newCall(Request.Builder().url(url).method("DELETE", null).build()).execute()
            resp.isSuccessful.also { resp.close() }
        }.getOrDefault(false)
    }

    // -- Full restore from Nextcloud --

    /**
     * Scans Vinci/Contacts/ on Nextcloud and restores:
     *   - isStarred and followUpDue from each index.md
     *   - All interaction logs from each calls/[timestamp].md
     *
     * Safe to call repeatedly - existing Room records are not duplicated
     * (logs matched by id derived from timestamp).
     *
     * Returns number of contacts restored and logs imported.
     */
    suspend fun restoreFromNextcloud(): RestoreResult = withContext(Dispatchers.IO) {
        val session = authRepository.session.first()
            ?: return@withContext RestoreResult(error = "Not logged in")

        val client  = authedClient(session)
        val base    = davBase(session)

        // 1. List contact folders via PROPFIND depth=1
        val contactFolders = listWebDavFolders(client, "$base/Contacts")
        var contactsRestored = 0
        var logsImported     = 0

        for (folder in contactFolders) {
            val folderUrl  = "$base/Contacts/${URLEncoder.encode(folder, "UTF-8")}"

            // 2. Read index.md (optional — only exists if contact was starred or had a follow-up)
            val indexMd = fetchText(client, "$folderUrl/index.md")
            val meta    = if (indexMd != null) parseIndexMd(indexMd) else emptyMap()

            // Derive uid from index.md if present; otherwise use the 8-char prefix from folder name
            // e.g. "jose-van-gelder-31624847" → prefix "31624847" → matches full uid in Room via LIKE
            val uidFromMeta   = meta["contact_uid"]
            val uidPrefix     = folder.substringAfterLast('-').ifBlank { folder }

            // 3. Resolve Room contact using four strategies in order:
            // Strategy 1: exact uid from index.md
            val byExactUid = if (uidFromMeta != null) contactDao.getContactByUid(uidFromMeta) else null

            // Strategy 2: prefix match on folder suffix vs cardavUid
            val byPrefix = if (byExactUid == null) contactDao.getContactByUidPrefix(uidPrefix) else null

            // Strategy 3: Kotlin-side name match — try contact_name from index.md first (most accurate),
            // then fall back to name derived from folder name
            val contact = byExactUid ?: byPrefix ?: run {
                // Lazy load — query Room only when uid strategies failed
                // This avoids the race condition where contacts haven't synced yet at loop start
                val nameFromMeta = meta["contact_name"]
                val namePart = when {
                    folder.contains('-') && folder.substringAfterLast('-').length == uidPrefix.length ->
                        folder.dropLast(uidPrefix.length + 1)
                    else -> folder
                }

                // Strategy 3a: exact display name from index.md
                val byExactName = if (nameFromMeta != null)
                    contactDao.getContactByExactName(nameFromMeta)
                else null

                // Strategy 3b: sanitised name match in Kotlin (handles emoji correctly)
                val bySanitisedName = if (byExactName == null) {
                    val allContacts = contactDao.getAllContactsSuspend()
                    allContacts.find { c ->
                        buildFolderName(c.displayName, "").trimEnd('-') == namePart
                    }
                } else null

                byExactName ?: bySanitisedName
            }


            // Use the resolved full uid from Room so interaction logs are stored correctly
            val uid  = contact?.cardavUid ?: uidFromMeta ?: uidPrefix
            val name = meta["contact_name"] ?: contact?.displayName ?: ""

            // Restore starred/followUp only when index.md was present
            if (indexMd != null && contact != null) {
                val starred  = meta["starred"] == "true"
                val followUp = meta["follow_up_due"]?.toLongOrNull() ?: 0L
                if (contact.isStarred != starred || contact.followUpDue != followUp) {
                    contactDao.setStarred(contact.id, starred)
                    contactDao.setFollowUp(contact.id, followUp)
                    contactsRestored++
                }
            }

            // 4. List and parse calls/[timestamp].md files (always attempted)
            val callFiles = listWebDavFiles(client, "$folderUrl/calls")
            for (callFile in callFiles) {
                if (!callFile.endsWith(".md")) continue
                val callMd = fetchText(client, "$folderUrl/calls/${URLEncoder.encode(callFile, "UTF-8")}") ?: continue
                val log    = parseCallLogMd(callMd, uid, name, contact?.id ?: -1L) ?: continue

                // Insert if new; re-link if existing record was orphaned (contactId = -1)
                val existing = callLogDao.getLogById(log.id)
                if (existing == null) {
                    callLogDao.insertLog(log)
                    logsImported++
                } else if (existing.contactId == -1L && log.contactId != -1L) {
                    callLogDao.insertLog(log) // REPLACE orphaned record with linked one
                    logsImported++
                }
            }
        }

        RestoreResult(contactsRestored = contactsRestored, logsImported = logsImported)
    }

    // -- WebDAV helpers --

    /**
     * Lists immediate child folder names under a WebDAV URL using PROPFIND depth=1.
     * Returns folder names only (not files, not the parent itself).
     */
    private fun listWebDavFolders(client: OkHttpClient, url: String): List<String> {
        val propfindBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <propfind xmlns="DAV:">
                <prop><resourcetype/><displayname/></prop>
            </propfind>
        """.trimIndent()

        return runCatching {
            val resp = client.newCall(
                Request.Builder()
                    .url(url)
                    .method("PROPFIND", propfindBody.toRequestBody("application/xml".toMediaType()))
                    .header("Depth", "1")
                    .build()
            ).execute()

            val body = resp.body?.string() ?: return emptyList()
            resp.close()

            // Parse <d:href> tags - skip the first one (parent folder)
            val hrefRegex = Regex("<[^>]*:href[^>]*>([^<]+)</[^>]*:href>", RegexOption.IGNORE_CASE)
            hrefRegex.findAll(body)
                .map { it.groupValues[1].trimEnd('/') }
                .map { it.substringAfterLast('/') }
                .filter { it.isNotBlank() && it != "Contacts" }
                .toList()
        }.getOrDefault(emptyList())
    }

    /**
     * Lists immediate child file names under a WebDAV URL using PROPFIND depth=1.
     */
    private fun listWebDavFiles(client: OkHttpClient, url: String): List<String> {
        val propfindBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <propfind xmlns="DAV:"><prop><resourcetype/></prop></propfind>
        """.trimIndent()

        return runCatching {
            val resp = client.newCall(
                Request.Builder()
                    .url(url)
                    .method("PROPFIND", propfindBody.toRequestBody("application/xml".toMediaType()))
                    .header("Depth", "1")
                    .build()
            ).execute()

            val body = resp.body?.string() ?: return emptyList()
            resp.close()

            val hrefRegex = Regex("<[^>]*:href[^>]*>([^<]+)</[^>]*:href>", RegexOption.IGNORE_CASE)
            // Only return entries that are NOT collections (i.e. files)
            val collectionRegex = Regex("<[^>]*:collection[^>]*[/]?>", RegexOption.IGNORE_CASE)
            val responses = body.split(Regex("</[^>]*:response>", RegexOption.IGNORE_CASE))

            responses.mapNotNull { block ->
                if (collectionRegex.containsMatchIn(block)) return@mapNotNull null
                val href = hrefRegex.find(block)?.groupValues?.get(1) ?: return@mapNotNull null
                val name = href.trimEnd('/').substringAfterLast('/')
                if (name.isBlank()) null else name
            }
        }.getOrDefault(emptyList())
    }

    private fun fetchText(client: OkHttpClient, url: String): String? {
        return runCatching {
            val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
            if (!resp.isSuccessful) return null
            resp.body?.string().also { resp.close() }
        }.getOrNull()
    }

    // -- Markdown parsers --

    /**
     * Parses index.md format:
     *   contact_uid: abc123
     *   starred: true
     *   follow_up_due: 1234567890
     */
    private fun parseIndexMd(content: String): Map<String, String> {
        return content.lines()
            .filter { it.contains(":") && !it.startsWith("#") }
            .associate { line ->
                val idx = line.indexOf(':')
                line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }
    }

    /**
     * Parses a call log markdown file back into a CallLogEntity.
     * Uses timestamp derived from the filename pattern yyyy-MM-dd-HHmm
     * as the stable ID to prevent duplicates.
     */
    private suspend fun parseCallLogMd(content: String, folderUid: String, folderName: String, folderContactId: Long): CallLogEntity? {
        return runCatching {
            val lines = content.lines()

            fun field(key: String): String {
                // Accept both **Key:** (current format) and plain Key: (older/manual format)
                val line = lines.firstOrNull { it.startsWith("**$key:**") }
                    ?: lines.firstOrNull { it.startsWith("$key:") }
                    ?: return ""
                return line.substringAfter("$key:").removePrefix("**").substringAfter(":**")
                    .trim().trimEnd().removeSuffix("  ")
            }

            // Read contact_uid/name embedded in file (new format) — fall back to folder-level values
            val fileUid  = lines.firstOrNull { it.startsWith("contact_uid:") }
                ?.substringAfter("contact_uid:")?.trim()
            val fileName = lines.firstOrNull { it.startsWith("contact_name:") }
                ?.substringAfter("contact_name:")?.trim()

            // Resolve contact: prefer uid from file, fall back to folder uid, then name
            val resolvedContact = if (fileUid != null) {
                contactDao.getContactByUid(fileUid)
                    ?: contactDao.getContactByUidPrefix(fileUid.take(8))
                    ?: if (folderContactId != -1L) contactDao.getContactById(folderContactId) else null
            } else {
                if (folderContactId != -1L) contactDao.getContactById(folderContactId) else null
            }
            val contactUid  = resolvedContact?.cardavUid ?: fileUid ?: folderUid
            val contactName = resolvedContact?.displayName ?: fileName ?: folderName
            val contactId   = resolvedContact?.id ?: folderContactId

            val typeAndName = lines.firstOrNull { it.startsWith("# ") } ?: return null
            val interactionType = typeAndName.removePrefix("# ").substringBefore(" -").trim()

            val dateStr    = field("Date")
            val reason     = field("Reason")
            val outcome    = field("Outcome")
            val followUpRaw= field("Follow-up")
            val direction  = field("Direction")

            // Parse timestamp - use as stable ID
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val ts  = runCatching { sdf.parse(dateStr)?.time ?: System.currentTimeMillis() }.getOrDefault(System.currentTimeMillis())

            // Parse notes - everything after "## Notes"
            val notesIdx = lines.indexOfFirst { it.trim() == "## Notes" }
            val notes = if (notesIdx >= 0) {
                lines.drop(notesIdx + 1)
                    .joinToString("\n")
                    .trim()
                    .removePrefix("_No notes recorded._")
                    .trim()
            } else ""

            val followUpDays = when {
                followUpRaw.startsWith("Yes") -> followUpRaw.filter { it.isDigit() }.toIntOrNull() ?: 0
                else -> 0
            }

            // Participants (extra people beyond the primary contact, e.g. group calls) —
            // stored as a raw JSON line so it round-trips exactly; falls back to "[]" if absent/old file.
            val participantsJson = lines.firstOrNull { it.startsWith("participants:") }
                ?.substringAfter("participants:")?.trim()?.ifBlank { "[]" } ?: "[]"

            CallLogEntity(
                id              = ts,  // timestamp as stable ID - prevents duplicates
                contactId       = contactId,
                contactUid      = contactUid,
                contactName     = contactName,
                phoneNumber     = "",
                callTimestamp   = ts,
                durationSeconds = 0,
                isOutgoing      = direction.lowercase() != "incoming",
                interactionType = interactionType.ifBlank { "Call" },
                reason          = reason.ifBlank { "Other" },
                outcome         = outcome.ifBlank { "Neutral" },
                notes           = notes,
                participants    = participantsJson,
                followUpDays    = followUpDays,
                isSynced        = true,
            )
        }.getOrNull()
    }

    // -- Shared helpers --

    internal fun buildFolderName(name: String, uid: String): String {
        val safeName = name.lowercase()
            .replace(Regex("[^a-z0-9 ]"), "")
            .trim()
            .replace(" ", "-")
        // Unknown numbers have no CardDAV uid -- use the (digits-only) name alone
        // so the folder is stable for that number without a trailing separator.
        return if (uid.isBlank()) safeName else "$safeName-${uid.take(8)}"
    }

    private fun buildMarkdown(log: CallLogEntity): String {
        val date     = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(log.callTimestamp))
        val followUp = if (log.followUpDays > 0) "Yes (${log.followUpDays} days)" else "No"
        val header   = "# ${log.interactionType} - ${log.contactName}"
        val participantNames = parseParticipants(log.participants).map { it.name }
        val meta = buildString {
            appendLine("contact_uid: ${log.contactUid}")
            appendLine("contact_name: ${log.contactName}")
            if (log.participants.isNotBlank() && log.participants != "[]") {
                appendLine("participants: ${log.participants}")
            }
            appendLine()
            appendLine("**Date:** $date  ")
            if (log.interactionType == "Call") {
                appendLine("**Direction:** ${if (log.isOutgoing) "Outgoing" else "Incoming"}  ")
                if (log.durationSeconds > 0) appendLine("**Duration:** ${formatDuration(log.durationSeconds)}  ")
            }
            if (participantNames.isNotEmpty()) {
                appendLine("**With:** ${participantNames.joinToString(", ")}  ")
            }
            appendLine("**Reason:** ${log.reason}  ")
            appendLine("**Outcome:** ${log.outcome}  ")
            appendLine("**Follow-up:** $followUp  ")
        }
        return "$header\n\n$meta\n## Notes\n\n${log.notes.ifBlank { "_No notes recorded._" }}"
    }

    private fun formatDuration(seconds: Int): String {
        val m = seconds / 60; val s = seconds % 60
        return if (m > 0) "${m}m ${s}s" else "${s}s"
    }
}

data class RestoreResult(
    val contactsRestored: Int = 0,
    val logsImported: Int = 0,
    val error: String? = null,
)
