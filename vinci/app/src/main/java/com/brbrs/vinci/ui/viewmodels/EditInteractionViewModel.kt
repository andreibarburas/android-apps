package com.brbrs.vinci.ui.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.vinci.data.AttachmentDao
import com.brbrs.vinci.data.AttachmentEntity
import com.brbrs.vinci.data.CallLogDao
import com.brbrs.vinci.data.CallLogEntity
import com.brbrs.vinci.data.ContactDao
import com.brbrs.vinci.data.ContactEntity
import com.brbrs.vinci.ui.theme.DisplayPreferencesRepository
import com.brbrs.vinci.network.WebDavRepository
import com.brbrs.vinci.tasks.TasksOrgHelper
import com.brbrs.vinci.tasks.TasksPreference
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import com.brbrs.vinci.util.InteractionParticipant
import com.brbrs.vinci.util.normalizePhone
import com.brbrs.vinci.util.parseParticipants
import com.brbrs.vinci.util.serializeParticipants
import org.json.JSONArray
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditInteractionUiState(
    val log: CallLogEntity? = null,
    val contact: ContactEntity? = null,
    val isUnknownNumber: Boolean = false,
    val contactNameInput: String = "",
    val interactionType: String = "Call",
    val reason: String = "",
    val outcome: String = "Positive",
    val notes: String = "",
    val selectedTags: List<String> = emptyList(),
    val availableTags: List<String> = emptyList(),
    val followUpEnabled: Boolean = false,
    val followUpDays: Int = 3,
    val interactionTimestamp: Long = System.currentTimeMillis(),
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val tasksEnabled: Boolean = false,
    val tasksInstalled: Boolean = false,
    val attachments: List<AttachmentEntity> = emptyList(),
    val isUploadingAttachment: Boolean = false,
    val attachmentError: String? = null,
    /** Social platforms available for this contact (e.g. ["linkedin","instagram"]),
     *  derived from their vCard social links. Empty if none or unknown contact. */
    val availableSocialTypes: List<String> = emptyList(),
    /** The specific social platform chosen when interactionType == "Social Media",
     *  e.g. "linkedin", "instagram". Empty means none chosen yet. */
    val selectedSocialPlatform: String = "",
    // Additional people in this interaction (e.g. group calls) beyond the primary contact
    val participants: List<InteractionParticipant> = emptyList(),
    val participantQuery: String = "",
    val participantSearchResults: List<ContactEntity> = emptyList(),
    // uid -> photoUri, so participant chips can show a picture without a lookup on every recomposition
    val participantPhotos: Map<String, String> = emptyMap(),
)

@HiltViewModel
class EditInteractionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val callLogDao: CallLogDao,
    private val contactDao: ContactDao,
    private val attachmentDao: AttachmentDao,
    private val displayPrefs: DisplayPreferencesRepository,
    private val webDavRepository: WebDavRepository,
    private val cardDavRepository: com.brbrs.vinci.network.CardDavRepository,
    private val tasksPref: TasksPreference,
) : ViewModel() {

    private val logId: Long = checkNotNull(savedStateHandle["logId"])
    private val _uiState = MutableStateFlow(EditInteractionUiState())
    val uiState: StateFlow<EditInteractionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val log     = callLogDao.getLogById(logId) ?: return@launch
            val contact = log.contactId?.let { contactDao.getContactById(it) }
            val tasksOn = tasksPref.enabled.first()
            val tagSets = callLogDao.getAllTagSets()
            val allTags = (tagSets.flatMap { parseTags(it) } + parseTags(log.tags)).distinct().sorted()
            _uiState.update {
                it.copy(
                    log                  = log,
                    contact              = contact,
                    isUnknownNumber      = log.contactId == null,
                    contactNameInput     = if (log.contactId == null) log.contactName else "",
                    interactionType      = if (log.interactionType.startsWith("Social Media:")) "Social Media" else log.interactionType,
                    selectedSocialPlatform = if (log.interactionType.startsWith("Social Media:")) log.interactionType.removePrefix("Social Media:") else "",
                    reason               = log.reason,
                    outcome              = log.outcome,
                    notes                = log.notes,
                    selectedTags         = parseTags(log.tags),
                    availableTags        = allTags,
                    followUpEnabled      = log.followUpDays > 0,
                    followUpDays         = if (log.followUpDays > 0) log.followUpDays else 3,
                    interactionTimestamp = log.callTimestamp,
                    tasksEnabled         = tasksOn,
                    tasksInstalled       = TasksOrgHelper.isInstalled(context),
                    participants         = parseParticipants(log.participants),
                )
            }

            // Resolve photos for participants already saved on this log (added in a previous session)
            val loadedParticipants = parseParticipants(log.participants)
            if (loadedParticipants.isNotEmpty()) {
                val photos = loadedParticipants
                    .filter { it.uid.isNotBlank() }
                    .mapNotNull { p -> contactDao.getContactByUid(p.uid)?.let { p.uid to it.photoUri } }
                    .toMap()
                if (photos.isNotEmpty()) {
                    _uiState.update { it.copy(participantPhotos = it.participantPhotos + photos) }
                }
            }

            // Fetch social platforms from vCard — contact is now guaranteed to be loaded
            if (contact != null && contact.cardavUid.isNotBlank()) {
                val linksJson = runCatching {
                    cardDavRepository.fetchSocialLinks(contact.cardavUid)
                }.getOrNull()
                if (!linksJson.isNullOrBlank()) {
                    val platforms = try {
                        val arr = org.json.JSONArray(linksJson)
                        (0 until arr.length())
                            .map { arr.getJSONObject(it).optString("platform", "") }
                            .filter { it.isNotBlank() && it != "other" }
                            .distinct()
                    } catch (e: Exception) { emptyList() }
                    if (platforms.isNotEmpty()) {
                        _uiState.update { it.copy(availableSocialTypes = platforms) }
                    }
                }
            }
        }

        viewModelScope.launch {
            attachmentDao.getAttachmentsForLog(logId).collect { list ->
                _uiState.update { it.copy(attachments = list) }
            }
        }
    }

    private fun parseTags(json: String): List<String> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) { emptyList() }
    }

    fun onInteractionTypeSelected(type: String) {
        _uiState.update { it.copy(interactionType = type, selectedSocialPlatform = if (type != "Social Media") "" else it.selectedSocialPlatform) }
    }
    fun onSocialPlatformSelected(platform: String) { _uiState.update { it.copy(selectedSocialPlatform = platform) } }
    fun onReasonSelected(reason: String)        { _uiState.update { it.copy(reason = reason) } }
    fun onOutcomeSelected(outcome: String)      { _uiState.update { it.copy(outcome = outcome) } }
    fun onNotesChanged(notes: String)           { _uiState.update { it.copy(notes = notes) } }
    fun onFollowUpToggled(enabled: Boolean)     { _uiState.update { it.copy(followUpEnabled = enabled) } }
    fun onFollowUpDaysChanged(days: Int)        { _uiState.update { it.copy(followUpDays = days) } }
    fun onTimestampChanged(ts: Long)            { _uiState.update { it.copy(interactionTimestamp = ts) } }
    fun onContactNameInputChanged(name: String) { _uiState.update { it.copy(contactNameInput = name) } }
    fun toggleTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isBlank()) return
        _uiState.update {
            val selected = if (trimmed in it.selectedTags) it.selectedTags - trimmed else it.selectedTags + trimmed
            val available = if (trimmed in it.availableTags) it.availableTags else (it.availableTags + trimmed).sorted()
            it.copy(selectedTags = selected, availableTags = available)
        }
    }
    fun showDeleteConfirm()                     { _uiState.update { it.copy(showDeleteConfirm = true) } }
    fun dismissDeleteConfirm()                  { _uiState.update { it.copy(showDeleteConfirm = false) } }

    // -- Participants (extra people in this interaction, e.g. group calls) --------------

    fun onParticipantQueryChanged(query: String) {
        _uiState.update { it.copy(participantQuery = query) }
        viewModelScope.launch {
            val state = _uiState.value
            if (query.isBlank()) {
                _uiState.update { it.copy(participantSearchResults = emptyList()) }
                return@launch
            }
            val existingUids = state.participants.mapNotNull { it.uid.ifBlank { null } }.toSet()
            val results = contactDao.searchContacts(query).first()
                .filter { c -> c.id != state.contact?.id && c.cardavUid !in existingUids }
                .take(8)
            _uiState.update { it.copy(participantSearchResults = results) }
        }
    }

    fun addParticipantContact(contact: ContactEntity) {
        _uiState.update {
            if (it.participants.any { p -> p.uid.isNotBlank() && p.uid == contact.cardavUid }) return@update it
            it.copy(
                participants = it.participants + InteractionParticipant(uid = contact.cardavUid, name = contact.displayName),
                participantQuery = "",
                participantSearchResults = emptyList(),
                participantPhotos = it.participantPhotos + (contact.cardavUid to contact.photoUri),
            )
        }
    }

    fun addParticipantFreeText(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        _uiState.update {
            if (it.participants.any { p -> p.name.equals(trimmed, ignoreCase = true) }) return@update it
            it.copy(
                participants = it.participants + InteractionParticipant(name = trimmed),
                participantQuery = "",
                participantSearchResults = emptyList(),
            )
        }
    }

    fun removeParticipant(participant: InteractionParticipant) {
        _uiState.update { it.copy(participants = it.participants - participant) }
    }

    fun save() {
        val state = _uiState.value
        val log   = state.log ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val updated = log.copy(
                contactName      = if (state.isUnknownNumber) state.contactNameInput.ifBlank { log.phoneNumber.ifBlank { "Unknown" } } else log.contactName,
                interactionType  = if (state.interactionType == "Social Media" && state.selectedSocialPlatform.isNotBlank())
                                       "Social Media:${state.selectedSocialPlatform}"
                                   else state.interactionType,
                reason           = state.reason.ifBlank { "Other" },
                outcome          = state.outcome,
                notes            = state.notes,
                tags             = JSONArray(state.selectedTags).toString(),
                participants     = serializeParticipants(state.participants),
                followUpDays     = if (state.followUpEnabled) state.followUpDays else 0,
                callTimestamp    = state.interactionTimestamp,
                isSynced         = false,
            )
            callLogDao.updateLog(updated)

            // Bump lastCallTimestamp for any participants who are existing contacts too,
            // so a group call also surfaces in their "recent contacts" on Home.
            state.participants.forEach { participant ->
                if (participant.uid.isNotBlank() && participant.uid != state.contact?.cardavUid) {
                    contactDao.getContactByUid(participant.uid)?.let {
                        contactDao.updateLastCall(it.id, state.interactionTimestamp)
                    }
                }
            }

            if (state.followUpEnabled && state.log.followUpDays == 0) {
                val contact = state.contact
                if (contact != null) {
                    val dueMs = System.currentTimeMillis() + (state.followUpDays.toLong() * 24 * 60 * 60 * 1000)
                    contactDao.setFollowUp(contact.id, dueMs)
                    if (state.tasksEnabled && state.tasksInstalled) {
                        TasksOrgHelper.createTask(context, "Follow up with ${contact.displayName}", "Re: ${updated.reason}\n\n${updated.notes}".trim())
                    }
                }
            }

            webDavRepository.uploadCallLog(updated)
            callLogDao.markSynced(updated.id)
            _uiState.update { it.copy(isSaving = false, saved = true) }
        }
    }

    fun delete() {
        val log = _uiState.value.log ?: return
        viewModelScope.launch {
            callLogDao.deleteLog(log.id)
            // Recalculate lastCallTimestamp from remaining logs so Home's "People"
            // section reflects the deletion correctly
            log.contactId?.let { contactId ->
                val latestTs = callLogDao.getLatestTimestampForContact(contactId) ?: 0L
                contactDao.updateLastCall(contactId, latestTs)
            }
            // Also delete from Nextcloud so it doesn't reappear on restore
            runCatching { webDavRepository.deleteCallLog(log) }
            _uiState.update { it.copy(deleted = true) }
        }
    }

    // -- Attachments --------------------------------------------------------

    /**
     * Reads each picked [uris], uploads it to Nextcloud, and stores an
     * [AttachmentEntity]. If the global "keep on device too" preference is
     * enabled, also copies the file into local app storage.
     */
    fun attachFiles(uris: List<android.net.Uri>) {
        val log = _uiState.value.log ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingAttachment = true, attachmentError = null) }
            val keepLocal = displayPrefs.preferences.first().attachmentsKeepLocal
            for (uri in uris) {
                runCatching {
                    val resolver = context.contentResolver
                    val (name, size) = queryFileMeta(uri)
                    val mimeType = resolver.getType(uri) ?: "application/octet-stream"
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw java.io.IOException("Could not read file")

                    val remotePath = webDavRepository.uploadAttachment(log, name, mimeType, bytes)
                        ?: throw java.io.IOException("Upload failed")

                    var localPath = ""
                    if (keepLocal) {
                        val dir = java.io.File(context.filesDir, "attachments/${log.id}")
                        if (!dir.exists()) dir.mkdirs()
                        val file = java.io.File(dir, name)
                        file.writeBytes(bytes)
                        localPath = file.absolutePath
                    }

                    attachmentDao.insert(
                        AttachmentEntity(
                            logId         = log.id,
                            fileName      = name,
                            mimeType      = mimeType,
                            sizeBytes     = size,
                            nextcloudPath = remotePath,
                            localPath     = localPath,
                            cachedLocally = keepLocal,
                            isSynced      = true,
                        )
                    )
                }.onFailure { e ->
                    _uiState.update { it.copy(attachmentError = "Failed to attach: ${e.message}") }
                }
            }
            _uiState.update { it.copy(isUploadingAttachment = false) }
        }
    }

    /** Resolves display name and size for a content [uri] via the content resolver. */
    private fun queryFileMeta(uri: android.net.Uri): Pair<String, Long> {
        var name = "file"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }
        return name to size
    }

    /**
     * Returns a sharable [content://] URI for the given attachment, downloading
     * it from Nextcloud first if it isn't cached locally. Returns null on failure.
     */
    fun openAttachment(attachment: AttachmentEntity, onResult: (android.net.Uri?) -> Unit) {
        viewModelScope.launch {
            val localFile = if (attachment.cachedLocally && attachment.localPath.isNotBlank()) {
                java.io.File(attachment.localPath).takeIf { it.exists() }
            } else null

            val fileToOpen = localFile ?: run {
                val keepLocal = displayPrefs.preferences.first().attachmentsKeepLocal
                val dir = if (keepLocal) {
                    java.io.File(context.filesDir, "attachments/${attachment.logId}")
                } else {
                    java.io.File(context.cacheDir, "attachments/${attachment.logId}")
                }
                if (!dir.exists()) dir.mkdirs()
                val dest = java.io.File(dir, attachment.fileName)
                val ok = webDavRepository.downloadAttachment(attachment.nextcloudPath, dest)
                if (!ok) { onResult(null); return@launch }
                if (keepLocal) {
                    attachmentDao.update(attachment.copy(localPath = dest.absolutePath, cachedLocally = true))
                }
                dest
            }

            val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.brbrs.vinci.fileprovider", fileToOpen)
            onResult(uri)
        }
    }

    /** Removes an attachment from Nextcloud, local cache, and the database. */
    fun deleteAttachment(attachment: AttachmentEntity) {
        viewModelScope.launch {
            runCatching { webDavRepository.deleteAttachment(attachment.nextcloudPath) }
            if (attachment.localPath.isNotBlank()) {
                runCatching { java.io.File(attachment.localPath).delete() }
            }
            attachmentDao.delete(attachment.id)
        }
    }

    fun clearAttachmentError() { _uiState.update { it.copy(attachmentError = null) } }
}
