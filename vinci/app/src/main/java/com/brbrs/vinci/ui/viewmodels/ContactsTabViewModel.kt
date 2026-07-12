package com.brbrs.vinci.ui.viewmodels

import com.brbrs.vinci.data.CallLogDao
import com.brbrs.vinci.data.ContactDao
import com.brbrs.vinci.data.ContactEntity
import com.brbrs.vinci.network.WebDavRepository
import com.brbrs.vinci.ui.theme.DisplayPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

data class ContactsTabUiState(
    val searchQuery: String = "",
    val isGridView: Boolean = false,
    // Contacts grouped by first letter, in display order. "#" for non-alphabetic.
    val groups: List<Pair<String, List<ContactEntity>>> = emptyList(),
    // Letters available for the alphabet sidebar (only letters with contacts)
    val availableLetters: List<String> = emptyList(),
    // Tag filter
    val availableTags: List<String> = emptyList(),
    val selectedTag: String? = null,
    // Bulk selection
    val selectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactsTabViewModel @Inject constructor(
    private val contactDao: ContactDao,
    private val callLogDao: CallLogDao,
    private val displayPrefs: DisplayPreferencesRepository,
    private val webDavRepository: WebDavRepository,
) : ViewModel() {

    private val _searchQuery   = MutableStateFlow("")
    private val _selectedTag   = MutableStateFlow<String?>(null)
    private val _availableTags = MutableStateFlow<List<String>>(emptyList())
    private val _taggedContactIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _selectionMode = MutableStateFlow(false)
    private val _selectedIds   = MutableStateFlow<Set<Long>>(emptySet())

    init {
        viewModelScope.launch {
            val tagSets = callLogDao.getAllTagSets()
            _availableTags.value = tagSets.flatMap(::parseTags).distinct().sorted()
        }
    }

    fun onSearchChanged(query: String) { _searchQuery.value = query }

    fun toggleGridView() {
        viewModelScope.launch {
            val current = displayPrefs.preferences.first().isGridView
            displayPrefs.setGridView(!current)
            runCatching { webDavRepository.uploadPreferences(displayPrefs.toMap()) }
        }
    }

    /** Filters the contacts list to only those with at least one interaction tagged [tag]. Pass null to clear. */
    fun selectTagFilter(tag: String?) {
        viewModelScope.launch {
            _selectedTag.value = tag
            _taggedContactIds.value = if (tag == null) emptySet() else callLogDao.getContactIdsWithTag(tag).toSet()
        }
    }

    // -- Bulk selection ----------------------------------------------------

    fun enterSelectionMode(contactId: Long) {
        _selectionMode.value = true
        _selectedIds.value = setOf(contactId)
    }

    fun toggleSelected(contactId: Long) {
        _selectedIds.update { current ->
            if (contactId in current) current - contactId else current + contactId
        }
        if (_selectedIds.value.isEmpty()) _selectionMode.value = false
    }

    fun exitSelectionMode() {
        _selectionMode.value = false
        _selectedIds.value = emptySet()
    }

    /** Stars all currently-selected contacts (used by the bulk action bar). */
    fun starSelected() {
        viewModelScope.launch {
            _selectedIds.value.forEach { id ->
                contactDao.setStarred(id, true)
                val contact = contactDao.getContactById(id) ?: return@forEach
                runCatching {
                    webDavRepository.writeIndexMd(
                        contactUid  = contact.cardavUid,
                        contactName = contact.displayName,
                        isStarred   = true,
                        followUpDue = contact.followUpDue,
                    )
                }
            }
            exitSelectionMode()
        }
    }

    /** Unstars all currently-selected contacts. */
    fun unstarSelected() {
        viewModelScope.launch {
            _selectedIds.value.forEach { id ->
                contactDao.setStarred(id, false)
                val contact = contactDao.getContactById(id) ?: return@forEach
                runCatching {
                    webDavRepository.writeIndexMd(
                        contactUid  = contact.cardavUid,
                        contactName = contact.displayName,
                        isStarred   = false,
                        followUpDue = contact.followUpDue,
                    )
                }
            }
            exitSelectionMode()
        }
    }

    /** Pushes index.md to Nextcloud for all currently-starred contacts. Used for initial sync. */
    fun syncStarredToNextcloud() {
        viewModelScope.launch {
            val starred = contactDao.getStarredContactsOnce()
            starred.forEach { contact ->
                runCatching {
                    webDavRepository.writeIndexMd(
                        contactUid  = contact.cardavUid,
                        contactName = contact.displayName,
                        isStarred   = true,
                        followUpDue = contact.followUpDue,
                    )
                }
            }
        }
    }

    val uiState: StateFlow<ContactsTabUiState> = combine(
        _searchQuery.debounce(200).flatMapLatest { query ->
            if (query.isBlank()) contactDao.getAllContacts()
            else contactDao.searchContacts(query)
        },
        _searchQuery,
        displayPrefs.preferences,
        combine(_selectedTag, _taggedContactIds, _availableTags) { tag, ids, available -> Triple(tag, ids, available) },
        combine(_selectionMode, _selectedIds) { mode, ids -> mode to ids },
    ) { contacts, query, prefs, tagState, selection ->
        val (selectedTag, taggedIds, availableTags) = tagState
        val (selectionMode, selectedIds) = selection
        val filtered = if (selectedTag != null) contacts.filter { it.id in taggedIds } else contacts
        val groups = if (query.isBlank()) {
            filtered
                .groupBy { letterFor(it.displayName) }
                .toSortedMap(compareBy { if (it == "#") "\u0000" else it }) // "#" first
                .map { (letter, list) -> letter to list }
        } else {
            emptyList()
        }
        ContactsTabUiState(
            searchQuery      = query,
            isGridView       = prefs.isGridView,
            groups           = groups,
            availableLetters = groups.map { it.first },
            availableTags    = availableTags,
            selectedTag      = selectedTag,
            selectionMode    = selectionMode,
            selectedIds      = selectedIds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ContactsTabUiState())

    // Flat search results (used when query is not blank)
    val searchResults: StateFlow<List<ContactEntity>> = _searchQuery.debounce(200).flatMapLatest { query ->
        if (query.isBlank()) flowOf(emptyList())
        else contactDao.searchContacts(query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun letterFor(name: String): String {
        val first = name.trim().firstOrNull()?.uppercaseChar() ?: '#'
        return if (first in 'A'..'Z') first.toString() else "#"
    }

    private fun parseTags(json: String): List<String> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
}
