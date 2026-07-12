package com.brbrs.vinci.ui.viewmodels

import android.content.Context
import android.provider.CallLog
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.vinci.data.CallLogDao
import com.brbrs.vinci.data.CallLogEntity
import com.brbrs.vinci.data.ContactDao
import com.brbrs.vinci.data.ContactEntity
import com.brbrs.vinci.network.WebDavRepository
import com.brbrs.vinci.ui.theme.DisplayPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// Threshold in ms — system call and Vinci log within 5 minutes = same interaction
private const val MATCH_THRESHOLD_MS = 5 * 60 * 1000L

data class RecentInteraction(
    val timestampMs: Long,
    val isOutgoing: Boolean,
    val durationSeconds: Int,
    val source: String,         // "system"
    val reason: String,
    val notes: String,
    val matchedLogId: Long?,    // non-null if a Vinci log exists for this call
)

data class ContactDetailUiState(
    val contact: ContactEntity? = null,
    val callLogs: List<CallLogEntity> = emptyList(),
    val showAllLogs: Boolean = false,
    val recentInteractions: List<RecentInteraction> = emptyList(),
    val showAllInteractions: Boolean = false,
    val defaultCountryCode: String = "",
    /** Social links fetched fresh from Nextcloud vCard (null = loading, "" = none found) */
    val remoteSocialLinks: String? = null,
)

@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val contactDao: ContactDao,
    private val callLogDao: CallLogDao,
    private val webDavRepository: WebDavRepository,
    private val cardDavRepository: com.brbrs.vinci.network.CardDavRepository,
    private val displayPrefs: DisplayPreferencesRepository,
) : ViewModel() {

    private val contactId: Long = checkNotNull(savedStateHandle["contactId"])
    private val _showAllLogs         = MutableStateFlow(false)
    private val _showAllInteractions = MutableStateFlow(false)
    private val _remoteSocialLinks   = MutableStateFlow<String?>(null) // null = not yet fetched
    private val _recentInteractions  = MutableStateFlow<List<RecentInteraction>>(emptyList())

    private val contactFlow = flow { emit(contactDao.getContactById(contactId)) }

    /**
     * Interactions logged against this contact directly, plus interactions where this contact
     * was added as an extra participant (e.g. a group call logged from someone else's profile).
     */
    private val mergedLogsFlow: Flow<List<CallLogEntity>> = contactFlow.flatMapLatest { contact ->
        val primaryFlow = callLogDao.getLogsForContact(contactId)
        val participantFlow = if (contact != null && contact.cardavUid.isNotBlank()) {
            callLogDao.getLogsAsParticipant(contact.cardavUid)
        } else {
            flowOf(emptyList())
        }
        combine(primaryFlow, participantFlow) { primary, asParticipant ->
            (primary + asParticipant).distinctBy { it.id }.sortedByDescending { it.callTimestamp }
        }
    }

    val uiState: StateFlow<ContactDetailUiState> = combine(
        contactFlow,
        mergedLogsFlow,
        combine(_showAllLogs, _showAllInteractions) { a, b -> a to b },
        combine(_recentInteractions, _remoteSocialLinks) { i, s -> i to s },
        displayPrefs.preferences,
    ) { contact, logs, showFlags, interactionsAndLinks, prefs ->
        val (showAll, showAllInt) = showFlags
        val (interactions, remoteSocialLinks) = interactionsAndLinks
        // Re-match interactions whenever Vinci logs change
        val matched = interactions.map { interaction ->
            val matchedLog = logs.firstOrNull { log ->
                kotlin.math.abs(log.callTimestamp - interaction.timestampMs) <= MATCH_THRESHOLD_MS
            }
            interaction.copy(matchedLogId = matchedLog?.id)
        }
        ContactDetailUiState(
            contact            = contact,
            callLogs           = logs,
            showAllLogs        = showAll,
            recentInteractions = matched,
            showAllInteractions= showAllInt,
            defaultCountryCode = prefs.defaultCountryCode,
            remoteSocialLinks  = remoteSocialLinks,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ContactDetailUiState())

    init {
        viewModelScope.launch {
            val contact = contactDao.getContactById(contactId) ?: run {
                return@launch
            }
            if (contact.cardavUid.isBlank()) {
                _remoteSocialLinks.value = ""
                return@launch
            }
            val links = runCatching { cardDavRepository.fetchSocialLinks(contact.cardavUid) }
                .onFailure {
 }
                .getOrNull()
            _remoteSocialLinks.value = links ?: contact.socialLinks.ifBlank { "" }
        }
    }

    init {
        viewModelScope.launch { loadRecentInteractions() }
    }

    fun toggleShowAllLogs()         { _showAllLogs.value = !_showAllLogs.value }
    fun toggleShowAllInteractions() { _showAllInteractions.value = !_showAllInteractions.value }

    fun toggleStarred() {
        viewModelScope.launch {
            val contact = contactDao.getContactById(contactId) ?: return@launch
            val newStarred = !contact.isStarred
            contactDao.setStarred(contactId, newStarred)
            webDavRepository.writeIndexMd(
                contactUid   = contact.cardavUid,
                contactName  = contact.displayName,
                isStarred    = newStarred,
                followUpDue  = contact.followUpDue,
            )
        }
    }

    private suspend fun loadRecentInteractions() = withContext(Dispatchers.IO) {
        val contact = contactDao.getContactById(contactId) ?: return@withContext
        val interactions = mutableListOf<RecentInteraction>()

        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.TYPE),
                "${CallLog.Calls.NUMBER} LIKE ?",
                arrayOf("%${contact.phoneNumber.takeLast(7)}"),
                "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                val dateCol = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durCol  = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val typeCol = cursor.getColumnIndex(CallLog.Calls.TYPE)
                while (cursor.moveToNext()) {
                    val type = cursor.getInt(typeCol)
                    interactions.add(
                        RecentInteraction(
                            timestampMs     = cursor.getLong(dateCol),
                            isOutgoing      = type == CallLog.Calls.OUTGOING_TYPE,
                            durationSeconds = cursor.getInt(durCol),
                            source          = "system",
                            reason          = "",
                            notes           = "",
                            matchedLogId    = null, // matched reactively in combine above
                        )
                    )
                }
            }
        } catch (e: SecurityException) { /* READ_CALL_LOG not granted */ }

        _recentInteractions.value = interactions
    }

    // -- Export this contact's history -----------------------------------

    private val _exportFile = MutableStateFlow<java.io.File?>(null)
    val exportFile: StateFlow<java.io.File?> = _exportFile.asStateFlow()
    fun clearExportFile() { _exportFile.value = null }

    /** Exports all logged interactions for this contact as a single Markdown file. */
    fun exportContactHistory() {
        viewModelScope.launch {
            val contact = uiState.value.contact ?: return@launch
            val primary = callLogDao.getLogsForContact(contactId).first()
            val asParticipant = if (contact.cardavUid.isNotBlank()) {
                callLogDao.getLogsAsParticipant(contact.cardavUid).first()
            } else emptyList()
            val logs = (primary + asParticipant).distinctBy { it.id }.sortedByDescending { it.callTimestamp }
            val dateFmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            val sb = StringBuilder()
            sb.append("# ${contact.displayName} — interaction history\n\n")
            sb.append("Exported ${dateFmt.format(java.util.Date())} -- ${logs.size} interactions\n\n")
            logs.forEach { log ->
                sb.append("## ${dateFmt.format(java.util.Date(log.callTimestamp))}\n")
                sb.append("- Type: ${log.interactionType}\n")
                if (log.contactId != contactId) sb.append("- With: ${log.contactName} (${contact.displayName} was a participant)\n")
                if (log.reason.isNotBlank())  sb.append("- Reason: ${log.reason}\n")
                if (log.outcome.isNotBlank()) sb.append("- Outcome: ${log.outcome}\n")
                val tags = try {
                    val arr = org.json.JSONArray(log.tags)
                    (0 until arr.length()).map { arr.getString(it) }
                } catch (e: Exception) { emptyList() }
                if (tags.isNotEmpty()) sb.append("- Tags: ${tags.joinToString(", ")}\n")
                if (log.notes.isNotBlank()) sb.append("\n${log.notes}\n")
                sb.append("\n")
            }

            val dir = java.io.File(context.cacheDir, "exports")
            if (!dir.exists()) dir.mkdirs()
            val safeName = contact.displayName.replace(Regex("[^A-Za-z0-9-_ ]"), "").trim().ifBlank { "contact" }
            val file = java.io.File(dir, "$safeName-history.md")
            file.writeText(sb.toString())
            _exportFile.value = file
        }
    }

    /** Returns a sharable content:// URI intent for the given exported file. */
    fun shareIntentFor(file: java.io.File): android.content.Intent {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.brbrs.vinci.fileprovider", file)
        return android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
