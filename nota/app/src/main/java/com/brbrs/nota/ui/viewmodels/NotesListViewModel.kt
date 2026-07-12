package com.brbrs.nota.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.brbrs.nota.auth.AuthRepository
import com.brbrs.nota.data.NoteDao
import com.brbrs.nota.data.NoteEntity
import com.brbrs.nota.network.SyncRepository
import com.brbrs.nota.tasks.TasksPreference
import com.brbrs.nota.ui.theme.ThemeRepository
import com.brbrs.nota.util.buildAuthenticatedImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

data class NotesListUiState(
    val notes: List<NoteEntity> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val searchQuery: String = "",
    val isSyncing: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotesListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteDao: NoteDao,
    private val syncRepository: SyncRepository,
    private val authRepository: AuthRepository,
    private val httpClient: OkHttpClient,
    private val themeRepository: ThemeRepository,
    private val tasksPref: TasksPreference,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow<String?>(null)
    private val _isSyncing = MutableStateFlow(false)

    val imageLoader: StateFlow<ImageLoader?> = authRepository.session
        .map { session ->
            session?.let { buildAuthenticatedImageLoader(context, it, httpClient) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isDark: StateFlow<Boolean> = themeRepository.isDark
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val tasksEnabled: StateFlow<Boolean> = tasksPref.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleTheme() {
        viewModelScope.launch { themeRepository.setDark(!isDark.value) }
    }

    val uiState: StateFlow<NotesListUiState> = combine(
        _searchQuery.debounce(250).flatMapLatest { query ->
            if (query.isBlank()) noteDao.getAllNotes() else noteDao.searchNotes(query)
        },
        _selectedCategory.flatMapLatest { cat ->
            if (cat == null) noteDao.getAllNotes() else noteDao.getNotesByCategory(cat)
        },
        noteDao.getAllCategories(),
        _searchQuery,
        _selectedCategory,
        _isSyncing,
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val searched  = args[0] as List<NoteEntity>
        val filtered  = args[1] as List<NoteEntity>
        val cats      = args[2] as List<String>
        val query     = args[3] as String
        val cat       = args[4] as String?
        val syncing   = args[5] as Boolean

        val notes = when {
            query.isNotBlank() -> searched
            cat != null -> filtered
            else -> searched
        }
        NotesListUiState(notes, cats, cat, query, syncing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotesListUiState())

    init { sync() }

    fun onSearchChanged(query: String) { _searchQuery.value = query }
    fun onCategorySelected(cat: String?) { _selectedCategory.value = cat }

    fun sync() {
        viewModelScope.launch {
            _isSyncing.value = true
            syncRepository.sync()
            _isSyncing.value = false
        }
    }
}
