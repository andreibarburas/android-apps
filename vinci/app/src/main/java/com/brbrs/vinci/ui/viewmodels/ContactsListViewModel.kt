package com.brbrs.vinci.ui.viewmodels

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.vinci.data.CallLogDao
import com.brbrs.vinci.data.CallLogEntity
import com.brbrs.vinci.data.ContactDao
import com.brbrs.vinci.data.ContactEntity
import com.brbrs.vinci.network.ContactsRepository
import com.brbrs.vinci.tasks.TasksPreference
import com.brbrs.vinci.ui.theme.DisplayPreferencesRepository
import com.brbrs.vinci.network.WebDavRepository
import com.brbrs.vinci.ui.theme.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.provider.CallLog
import com.brbrs.vinci.util.normalizePhone
import javax.inject.Inject

data class UnknownRecentCall(
    val phoneNumber: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val durationSeconds: Int,
)

data class ContactsListUiState(
    val followUpContacts: List<ContactEntity> = emptyList(),
    val upcomingBirthdays: List<Pair<ContactEntity, Int>> = emptyList(), // contact, daysUntil (0 = today)
    val recentUnknownCalls: List<UnknownRecentCall> = emptyList(),
    val starredContacts: List<ContactEntity> = emptyList(),
    val recentInteractions: List<CallLogEntity> = emptyList(),
    val recentContacts: List<ContactEntity> = emptyList(),
    val allContacts: List<ContactEntity> = emptyList(),
    val searchQuery: String = "",
    val isSyncing: Boolean = false,
    val isGridView: Boolean = false,
    val showRecentCalls: Boolean = true,
    val showAllRecent: Boolean = false,
    val showRecentInteractions: Boolean = true,
    val showAllRecentInteractions: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactsListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactDao: ContactDao,
    private val callLogDao: CallLogDao,
    private val contactsRepository: ContactsRepository,
    private val themeRepository: ThemeRepository,
    private val displayPrefs: DisplayPreferencesRepository,
    private val webDavRepository: WebDavRepository,
    private val tasksPref: TasksPreference,
) : ViewModel() {

    private val _searchQuery               = MutableStateFlow("")
    private val _isSyncing                 = MutableStateFlow(false)
    private val _showAllRecent             = MutableStateFlow(false)
    private val _showAllRecentInteractions = MutableStateFlow(false)
    private val _unknownRecentCalls        = MutableStateFlow<List<UnknownRecentCall>>(emptyList())

    val isDark: StateFlow<Boolean> = themeRepository.isDark
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val tasksEnabled: StateFlow<Boolean> = tasksPref.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleTheme() { viewModelScope.launch { themeRepository.setDark(!isDark.value) } }

    fun toggleGridView() {
        viewModelScope.launch {
            val current = displayPrefs.preferences.first().isGridView
            displayPrefs.setGridView(!current)
            uploadPrefsToNextcloud()
        }
    }

    fun toggleShowRecent() {
        viewModelScope.launch {
            val current = displayPrefs.preferences.first().showRecentCalls
            displayPrefs.setShowRecentCalls(!current)
            _showAllRecent.value = false
            uploadPrefsToNextcloud()
        }
    }

    fun toggleShowAllRecent() { _showAllRecent.value = !_showAllRecent.value }

    fun toggleShowRecentInteractions() {
        viewModelScope.launch {
            val current = displayPrefs.preferences.first().showRecentInteractions
            displayPrefs.setShowRecentInteractions(!current)
            _showAllRecentInteractions.value = false
            uploadPrefsToNextcloud()
        }
    }

    private suspend fun uploadPrefsToNextcloud() {
        runCatching { webDavRepository.uploadPreferences(displayPrefs.toMap()) }
    }

    fun toggleShowAllRecentInteractions() {
        _showAllRecentInteractions.value = !_showAllRecentInteractions.value
    }

    val uiState: StateFlow<ContactsListUiState> = combine(
        // Group 1: contact lists
        combine(
            _searchQuery.debounce(250).flatMapLatest { query ->
                if (query.isBlank()) contactDao.getAllContacts()
                else contactDao.searchContacts(query)
            },
            contactDao.getFollowUpsDue(System.currentTimeMillis()),
            contactDao.getRecentContacts(),
            contactDao.getStarredContacts(),
            contactDao.getContactsWithBirthdays(),
        ) { searched, followUps, recent, starred, birthdays ->
            listOf(searched, followUps, recent, starred, birthdays)
        },
        // Group 2: recent Vinci interactions + display prefs
        combine(
            callLogDao.getRecentLogs(),
            displayPrefs.preferences,
        ) { logs, prefs -> Pair(logs, prefs) },
        // Group 3: transient state
        _searchQuery,
        combine(_isSyncing, _unknownRecentCalls) { syncing, unknown -> Pair(syncing, unknown) },
        combine(_showAllRecent, _showAllRecentInteractions) { r, i -> Pair(r, i) },
    ) { contactLists, logsAndPrefs, query, syncingAndUnknown, showAlls ->
        val syncing       = syncingAndUnknown.first
        val unknownCalls  = syncingAndUnknown.second
        @Suppress("UNCHECKED_CAST")
        val searched      = contactLists[0] as List<ContactEntity>
        val followUps     = contactLists[1] as List<ContactEntity>
        val recent        = contactLists[2] as List<ContactEntity>
        val starred       = contactLists[3] as List<ContactEntity>
        val withBirthdays = contactLists[4] as List<ContactEntity>
        val upcomingBirthdays = withBirthdays
            .mapNotNull { c -> daysUntilBirthday(c.birthday)?.let { c to it } }
            .filter { it.second in 0..6 }
            .sortedBy { it.second }
        val recentLogs    = logsAndPrefs.first
        val prefs         = logsAndPrefs.second
        val showAllRecent = showAlls.first
        val showAllInt    = showAlls.second

        ContactsListUiState(
            allContacts               = searched,
            upcomingBirthdays         = if (query.isBlank()) upcomingBirthdays else emptyList(),
            recentUnknownCalls        = if (query.isBlank()) unknownCalls else emptyList(),
            followUpContacts          = if (query.isBlank()) followUps else emptyList(),
            starredContacts           = if (query.isBlank()) starred.filter { s -> followUps.none { it.id == s.id } } else emptyList(),
            recentInteractions        = if (query.isBlank()) recentLogs else emptyList(),
            recentContacts            = if (query.isBlank()) recent.filter { r ->
                followUps.none { it.id == r.id } && starred.none { it.id == r.id }
            } else emptyList(),
            searchQuery               = query,
            isSyncing                 = syncing,
            isGridView                = prefs.isGridView,
            showRecentCalls           = prefs.showRecentCalls,
            showAllRecent             = showAllRecent,
            showRecentInteractions    = prefs.showRecentInteractions,
            showAllRecentInteractions = showAllInt,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ContactsListUiState())

    init {
        sync()
        loadUnknownRecentCalls()
    }

    fun onSearchChanged(query: String) { _searchQuery.value = query }

    fun sync() {
        viewModelScope.launch {
            _isSyncing.value = true
            // Clear cached CardDAV address book URL so it re-discovers on next fetch
            // (handles cases where the cached URL is stale or wrong)
            runCatching { webDavRepository.clearAddressBookUrlCache() }
            runCatching { contactsRepository.syncFromDevice() }
            runCatching { webDavRepository.syncPendingLogs() }
            // Pull down anything logged elsewhere (e.g. the Nextcloud web app) that isn't
            // in the local database yet. restoreFromNextcloud() only inserts what's missing --
            // it never overwrites or deletes -- so it's safe to run on every regular sync,
            // not just from the explicit "Restore" action in Settings.
            runCatching { webDavRepository.restoreFromNextcloud() }
            // Cleans up duplicate rows left behind by a now-fixed id bug (see stableLogId).
            runCatching { webDavRepository.dedupeCallLogs() }
            _isSyncing.value = false
            loadUnknownRecentCalls()
        }
    }

    /**
     * Reads the system call log for recent calls from numbers that do NOT
     * resolve to a saved contact via PhoneLookup, so they can be shown
     * separately on Home alongside known contacts.
     */
    private fun loadUnknownRecentCalls() {
        viewModelScope.launch(Dispatchers.IO) {
            val results = mutableListOf<UnknownRecentCall>()
            val seen = mutableSetOf<String>()
            runCatching {
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(
                        CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.DURATION,
                        CallLog.Calls.TYPE,
                    ),
                    null, null,
                    "${CallLog.Calls.DATE} DESC",
                )?.use { cursor ->
                    val numCol      = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                    val dateCol     = cursor.getColumnIndex(CallLog.Calls.DATE)
                    val durCol      = cursor.getColumnIndex(CallLog.Calls.DURATION)
                    val typeCol     = cursor.getColumnIndex(CallLog.Calls.TYPE)
                    while (cursor.moveToNext() && results.size < 8) {
                        val number   = cursor.getString(numCol) ?: continue
                        val normalized = normalizePhone(number)
                        if (normalized.isBlank() || normalized in seen) continue
                        seen.add(normalized)
                        if (resolveContactId(number) != null) continue // matches a saved contact -- skip
                        results.add(
                            UnknownRecentCall(
                                phoneNumber     = number,
                                timestamp       = cursor.getLong(dateCol),
                                isOutgoing      = cursor.getInt(typeCol) == CallLog.Calls.OUTGOING_TYPE,
                                durationSeconds = cursor.getInt(durCol),
                            )
                        )
                    }
                }
            }
            _unknownRecentCalls.value = results.take(5)
        }
    }

    /** Resolves a phone number to a contact ID via PhoneLookup, or null if no match. */
    private fun resolveContactId(number: String): Long? {
        if (number.isBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup._ID),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        } catch (e: Exception) { null }
    }

    /**
     * Returns the number of days from today until this contact's next birthday
     * (0 = today, 1 = tomorrow, etc.), or null if the birthday string is invalid.
     * Birthday is stored as "YYYY-MM-DD".
     */
    private fun daysUntilBirthday(birthday: String): Int? {
        if (birthday.isBlank()) return null
        val parts = birthday.split("-")
        if (parts.size != 3) return null
        val month = parts[1].toIntOrNull() ?: return null
        val day   = parts[2].toIntOrNull() ?: return null

        val today = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val next = (today.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.MONTH, month - 1)
            set(java.util.Calendar.DAY_OF_MONTH, day)
        }
        if (next.before(today)) {
            next.set(java.util.Calendar.YEAR, next.get(java.util.Calendar.YEAR) + 1)
        }
        val diffMs = next.timeInMillis - today.timeInMillis
        return (diffMs / (24 * 60 * 60 * 1000)).toInt()
    }
}
