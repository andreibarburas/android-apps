package com.brbrs.merk.ui.screens.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.merk.auth.AuthManager
import com.brbrs.merk.data.local.BookmarkEntity
import com.brbrs.merk.data.local.FolderEntity
import com.brbrs.merk.data.repository.BookmarkRepository
import com.brbrs.merk.tasks.TasksPreference
import com.brbrs.merk.ui.theme.ThemeRepository
import com.brbrs.merk.ui.theme.ViewModePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.Credentials
import javax.inject.Inject

data class ListUiState(
    val bookmarks: List<BookmarkEntity>  = emptyList(),
    val folders: List<FolderEntity>      = emptyList(),
    val selectedTag: String?             = null,
    val selectedFolderId: Long?          = null,
    val searchQuery: String              = "",
    val isSyncing: Boolean               = false,
    val syncError: String?               = null,
    val tasksEnabled: Boolean            = false,
    val isDark: Boolean                  = true,
    val serverUrl: String                = "",
    val authHeader: String               = "",
    val useCardView: Boolean             = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookmarkListViewModel @Inject constructor(
    private val repo: BookmarkRepository,
    private val tasksPref: TasksPreference,
    private val themeRepo: ThemeRepository,
    private val authManager: AuthManager,
    private val viewModePref: ViewModePreference,
) : ViewModel() {

    private val _searchQuery    = MutableStateFlow("")
    private val _selectedTag    = MutableStateFlow<String?>(null)
    private val _selectedFolder = MutableStateFlow<Long?>(null)
    private val _isSyncing      = MutableStateFlow(false)
    private val _syncError      = MutableStateFlow<String?>(null)

    // Pre-combine filters (tag + folder + query) — stays within 5-flow limit
    private val _filters = combine(
        _searchQuery, _selectedTag, _selectedFolder,
    ) { q, tag, folderId -> Triple(q, tag, folderId) }

    private val _context = combine(
        _filters, _isSyncing, _syncError, tasksPref.enabled, themeRepo.isDark,
    ) { (q, tag, folderId), syncing, error, tasks, dark ->
        QueryContext(
            query    = q as String,
            tag      = tag as String?,
            folderId = folderId as Long?,
            syncing  = syncing,
            error    = error,
            tasks    = tasks,
            dark     = dark,
            creds    = null,
        )
    }

    val uiState: StateFlow<ListUiState> = combine(
        _context, authManager.credentials, repo.observeFolders(), viewModePref.useCardView,
    ) { ctx, creds, folders, cardView ->
        ctx.copy(creds = creds, folders = folders, cardView = cardView)
    }.flatMapLatest { ctx ->
        val bookmarkFlow = when {
            ctx.query.isNotBlank()  -> repo.search(ctx.query)
            ctx.tag != null         -> repo.filterByTag(ctx.tag)
            ctx.folderId != null    -> repo.filterByFolder(ctx.folderId)
            else                    -> repo.observeAll()
        }
        bookmarkFlow.map { bms ->
            ListUiState(
                bookmarks        = bms,
                folders          = ctx.folders,
                selectedTag      = ctx.tag,
                selectedFolderId = ctx.folderId,
                searchQuery      = ctx.query,
                isSyncing        = ctx.syncing,
                syncError        = ctx.error,
                tasksEnabled     = ctx.tasks,
                isDark           = ctx.dark,
                serverUrl        = ctx.creds?.serverUrl?.trimEnd('/') ?: "",
                authHeader       = ctx.creds?.let {
                    Credentials.basic(it.username, it.appPassword)
                } ?: "",
                useCardView      = ctx.cardView,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState())

    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags: StateFlow<List<String>> = _tags

    init {
        sync()
        viewModelScope.launch { _tags.value = repo.getAllTags() }
    }

    fun onSearchChanged(q: String) { _searchQuery.value = q }

    fun onTagSelected(tag: String?) {
        _selectedTag.value    = if (_selectedTag.value == tag) null else tag
        _selectedFolder.value = null  // clear folder when tag is picked
    }

    fun onFolderSelected(folderId: Long?) {
        _selectedFolder.value = if (_selectedFolder.value == folderId) null else folderId
        _selectedTag.value    = null  // clear tag when folder is picked
    }

    fun onDeleteBookmark(id: Long) {
        viewModelScope.launch {
            repo.markForDelete(id)
            repo.sync()
        }
    }

    fun toggleTheme() {
        viewModelScope.launch { themeRepo.setDark(!uiState.value.isDark) }
    }

    fun toggleViewMode() {
        viewModelScope.launch { viewModePref.setUseCardView(!uiState.value.useCardView) }
    }

    fun sync() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            repo.sync().onFailure { _syncError.value = it.message }
            _isSyncing.value = false
            _tags.value = repo.getAllTags()
        }
    }

    fun dismissSyncError() { _syncError.value = null }
}

private data class QueryContext(
    val query:    String,
    val tag:      String?,
    val folderId: Long?,
    val syncing:  Boolean,
    val error:    String?,
    val tasks:    Boolean,
    val dark:     Boolean,
    val creds:    com.brbrs.merk.auth.AuthCredentials?,
    val folders:  List<com.brbrs.merk.data.local.FolderEntity> = emptyList(),
    val cardView: Boolean = false,
)
