package com.brbrs.vinci.ui.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.vinci.data.CallLogDao
import com.brbrs.vinci.data.CallLogEntity
import com.brbrs.vinci.data.ContactDao
import com.brbrs.vinci.data.ContactEntity
import com.brbrs.vinci.network.WebDavRepository
import com.brbrs.vinci.tasks.TasksOrgHelper
import com.brbrs.vinci.tasks.TasksPreference
import com.brbrs.vinci.util.InteractionParticipant
import com.brbrs.vinci.util.normalizePhone
import com.brbrs.vinci.util.serializeParticipants
import com.brbrs.vinci.util.stableLogId
import org.json.JSONArray
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.util.Calendar
import javax.inject.Inject

// Interaction types
val INTERACTION_TYPES = listOf("Call", "Meeting", "Email", "Message", "Social Media", "Other")

// Reasons — broad enough to cover all interaction types
val INTERACTION_REASONS = listOf(
    "Catch-up", "Follow-up", "Intro", "Proposal",
    "Budget", "Support", "Planning", "Feedback", "Other"
)

val INTERACTION_OUTCOMES = listOf("Positive", "Neutral", "No answer", "Negative")

// Keep old names as aliases so existing references compile
val CALL_REASONS  = INTERACTION_REASONS
val CALL_OUTCOMES = INTERACTION_OUTCOMES

data class LogInteractionUiState(
    val contact: ContactEntity? = null,
    // Unknown-number mode (contact == null): phone number + user-entered label
    val phoneNumber: String = "",
    val contactNameInput: String = "",
    val isUnknownNumber: Boolean = false,
    // Previous interactions logged for this number (shown as context in unknown-number mode)
    val pastLogsForNumber: List<CallLogEntity> = emptyList(),
    val interactionType: String = "Call",
    val reason: String = "",
    val outcome: String = "Positive",
    val notes: String = "",
    val selectedTags: List<String> = emptyList(),
    val availableTags: List<String> = emptyList(),
    val followUpEnabled: Boolean = false,
    val followUpDays: Int = 3,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val tasksEnabled: Boolean = false,
    val tasksInstalled: Boolean = false,
    // Timestamp the user can edit (for backdating)
    val interactionTimestamp: Long = System.currentTimeMillis(),
    val availableSocialTypes: List<String> = emptyList(),
    val selectedSocialPlatform: String = "",
    // Additional people in this interaction (e.g. group calls) beyond the primary contact
    val participants: List<InteractionParticipant> = emptyList(),
    val participantQuery: String = "",
    val participantSearchResults: List<ContactEntity> = emptyList(),
    // uid -> photoUri, so participant chips can show a picture without a lookup on every recomposition
    val participantPhotos: Map<String, String> = emptyMap(),
)

// Keep old name as alias
typealias CallLogUiState = LogInteractionUiState

@HiltViewModel
class CallLogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val contactDao: ContactDao,
    private val callLogDao: CallLogDao,
    private val webDavRepository: WebDavRepository,
    private val cardDavRepository: com.brbrs.vinci.network.CardDavRepository,
    private val tasksPref: TasksPreference,
) : ViewModel() {

    private val contactId: Long        = checkNotNull(savedStateHandle["contactId"])
    private val prefillTimestamp: Long = savedStateHandle["prefillTimestamp"] ?: 0L
    private val prefillType: String    = URLDecoder.decode(savedStateHandle["prefillType"] ?: "Call", "UTF-8")
    private val prefillPhone: String   = URLDecoder.decode(savedStateHandle["phone"] ?: "", "UTF-8")

    private val _uiState = MutableStateFlow(LogInteractionUiState())
    val uiState: StateFlow<LogInteractionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val tasksOn = tasksPref.enabled.first()
            val tagSets = callLogDao.getAllTagSets()
            val allTags = tagSets.flatMap { parseTags(it) }.distinct().sorted()

            if (contactId < 0) {
                // Unknown number -- no contact, show past logs for this number for context
                val normalized = normalizePhone(prefillPhone)
                val pastLogs = if (normalized.isNotBlank()) {
                    callLogDao.getLogsForUnknownNumber(normalized).first()
                } else emptyList()
                _uiState.update {
                    it.copy(
                        contact              = null,
                        isUnknownNumber      = true,
                        phoneNumber          = prefillPhone,
                        pastLogsForNumber    = pastLogs,
                        tasksEnabled         = tasksOn,
                        tasksInstalled       = TasksOrgHelper.isInstalled(context),
                        interactionType      = prefillType,
                        availableTags        = allTags,
                        interactionTimestamp = if (prefillTimestamp > 0L) prefillTimestamp else System.currentTimeMillis(),
                    )
                }
            } else {
                val contact = contactDao.getContactById(contactId)
                _uiState.update {
                    it.copy(
                        contact              = contact,
                        tasksEnabled         = tasksOn,
                        tasksInstalled       = TasksOrgHelper.isInstalled(context),
                        interactionType      = prefillType,
                        availableTags        = allTags,
                        interactionTimestamp = if (prefillTimestamp > 0L) prefillTimestamp else System.currentTimeMillis(),
                    )
                }
                // Fetch social platforms from vCard
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

    /** Toggles a tag on/off. New tags (not in availableTags) are added to the available list. */
    fun toggleTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isBlank()) return
        _uiState.update {
            val selected = if (trimmed in it.selectedTags) it.selectedTags - trimmed else it.selectedTags + trimmed
            val available = if (trimmed in it.availableTags) it.availableTags else (it.availableTags + trimmed).sorted()
            it.copy(selectedTags = selected, availableTags = available)
        }
    }

    fun save() {
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val tagsJson = JSONArray(state.selectedTags).toString()
            val contact  = state.contact

            val log = if (contact != null) {
                CallLogEntity(
                    id              = stableLogId(state.interactionTimestamp),
                    contactId       = contact.id,
                    contactUid      = contact.cardavUid,
                    contactName     = contact.displayName,
                    phoneNumber     = contact.phoneNumber,
                    normalizedPhone = normalizePhone(contact.phoneNumber),
                    callTimestamp   = state.interactionTimestamp,
                    durationSeconds = 0,
                    isOutgoing      = true,
                    interactionType = if (state.interactionType == "Social Media" && state.selectedSocialPlatform.isNotBlank()) "Social Media:${state.selectedSocialPlatform}" else state.interactionType,
                    reason          = state.reason.ifBlank { "Other" },
                    outcome         = state.outcome,
                    notes           = state.notes,
                    tags            = tagsJson,
                    participants    = serializeParticipants(state.participants),
                    followUpDays    = if (state.followUpEnabled) state.followUpDays else 0,
                    isSynced        = false,
                )
            } else {
                // Unknown-number interaction -- no contact, store the user's label as contactName
                CallLogEntity(
                    id              = stableLogId(state.interactionTimestamp),
                    contactId       = null,
                    contactUid      = "",
                    contactName     = state.contactNameInput.ifBlank { state.phoneNumber.ifBlank { "Unknown" } },
                    phoneNumber     = state.phoneNumber,
                    normalizedPhone = normalizePhone(state.phoneNumber),
                    callTimestamp   = state.interactionTimestamp,
                    durationSeconds = 0,
                    isOutgoing      = true,
                    interactionType = if (state.interactionType == "Social Media" && state.selectedSocialPlatform.isNotBlank()) "Social Media:${state.selectedSocialPlatform}" else state.interactionType,
                    reason          = state.reason.ifBlank { "Other" },
                    outcome         = state.outcome,
                    notes           = state.notes,
                    tags            = tagsJson,
                    participants    = serializeParticipants(state.participants),
                    followUpDays    = 0,
                    isSynced        = false,
                )
            }

            callLogDao.insertLog(log)

            // Bump lastCallTimestamp for any participants who are existing contacts too,
            // so a group call also surfaces in their "recent contacts" on Home.
            state.participants.forEach { participant ->
                if (participant.uid.isNotBlank() && participant.uid != contact?.cardavUid) {
                    contactDao.getContactByUid(participant.uid)?.let {
                        contactDao.updateLastCall(it.id, state.interactionTimestamp)
                    }
                }
            }

            if (contact != null) {
                contactDao.updateLastCall(contact.id, state.interactionTimestamp)

                if (state.followUpEnabled) {
                    val now   = System.currentTimeMillis()
                    val dueMs = now + (state.followUpDays.toLong() * 24 * 60 * 60 * 1000)
                    contactDao.setFollowUp(contact.id, dueMs)
                    webDavRepository.writeIndexMd(
                        contactUid  = contact.cardavUid,
                        contactName = contact.displayName,
                        isStarred   = contact.isStarred,
                        followUpDue = dueMs,
                    )
                    if (state.tasksEnabled && state.tasksInstalled) {
                        val taskTitle = "Follow up with ${contact.displayName}"
                        val taskNotes = "Re: ${log.reason}\n\n${log.notes}".trim()
                        TasksOrgHelper.createTask(context, taskTitle, taskNotes)
                    }
                }

                webDavRepository.uploadCallLog(log)
                callLogDao.markSynced(log.id)
            }
            // Unknown-number logs stay local-only (no contact folder on Nextcloud to write to)
            // until the number is linked to a contact -- see ContactsRepository re-link logic.

            _uiState.update { it.copy(isSaving = false, saved = true) }
        }
    }
}
