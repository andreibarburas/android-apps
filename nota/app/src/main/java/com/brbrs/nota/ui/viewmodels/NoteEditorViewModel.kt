package com.brbrs.nota.ui.viewmodels

import android.content.Context
import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.brbrs.nota.biometric.BiometricHelper
import com.brbrs.nota.data.NoteDao
import com.brbrs.nota.network.ImageUploadRepository
import com.brbrs.nota.network.SyncRepository
import com.brbrs.nota.network.UploadResult
import com.brbrs.nota.tasks.TasksPreference
import com.brbrs.nota.util.buildAuthenticatedImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

data class NoteEditorUiState(
    val title: String = "",
    val contentField: TextFieldValue = TextFieldValue(""),
    val category: String = "",
    val isLocked: Boolean = false,
    val showLockOverlay: Boolean = false,
    val previewMode: Boolean = false,
    val isLoading: Boolean = true,
    val isUploading: Boolean = false,
    val uploadError: String? = null,
) {
    // Convenience accessor so call sites that only need the raw string don't change
    val content: String get() = contentField.text
}

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteDao: NoteDao,
    private val syncRepository: SyncRepository,
    private val imageUploadRepository: ImageUploadRepository,
    private val authRepository: com.brbrs.nota.auth.AuthRepository,
    private val httpClient: OkHttpClient,
    private val tasksPref: TasksPreference,
    val biometricHelper: BiometricHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteEditorUiState())
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    val categories: StateFlow<List<String>> = noteDao.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val imageLoader: StateFlow<ImageLoader?> = authRepository.session
        .map { session ->
            session?.let { buildAuthenticatedImageLoader(context, it, httpClient) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tasksEnabled: StateFlow<Boolean> = tasksPref.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var currentNoteId: Long = -1L

    fun loadNote(id: Long, initialCategory: String = "", sharedText: String = "") {
        currentNoteId = id
        if (id == -1L) {
            _uiState.update {
                it.copy(
                    category     = initialCategory,
                    contentField = TextFieldValue(sharedText, TextRange(sharedText.length)),
                    isLoading    = false,
                )
            }
            return
        }
        viewModelScope.launch {
            val note = noteDao.getNoteById(id)
            if (note != null) {
                _uiState.update {
                    it.copy(
                        title           = note.title,
                        contentField    = TextFieldValue(note.content, TextRange(note.content.length)),
                        category        = note.category,
                        isLocked        = note.isLocked,
                        showLockOverlay = note.isLocked,
                        isLoading       = false,
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun uploadAndInsertImage(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, uploadError = null) }
            when (val result = imageUploadRepository.uploadImage(uri)) {
                is UploadResult.Success -> {
                    insertAtCursor("\n\n${result.markdownLink}\n\n")
                    _uiState.update { it.copy(isUploading = false) }
                }
                is UploadResult.Error -> {
                    _uiState.update { it.copy(isUploading = false, uploadError = result.message) }
                }
            }
        }
    }

    fun clearUploadError() { _uiState.update { it.copy(uploadError = null) } }

    fun onTitleChanged(v: String)           { _uiState.update { it.copy(title = v) } }
    fun onContentChanged(v: TextFieldValue) { _uiState.update { it.copy(contentField = v) } }
    fun onCategoryChanged(v: String)        { _uiState.update { it.copy(category = v) } }

    fun togglePreview() { _uiState.update { it.copy(previewMode = !it.previewMode) } }

    fun toggleLock() {
        val newLocked = !_uiState.value.isLocked
        _uiState.update { it.copy(isLocked = newLocked) }
        if (currentNoteId > 0) {
            viewModelScope.launch { noteDao.setNoteLocked(currentNoteId, newLocked) }
        }
    }

    fun unlockNote() { _uiState.update { it.copy(showLockOverlay = false) } }

    /** Insert [snippet] at the current cursor position (or replace selection). */
    fun insertMarkdown(snippet: String) { insertAtCursor(snippet) }

    private fun insertAtCursor(snippet: String) {
        val current = _uiState.value.contentField
        val cursor  = current.selection.end.coerceIn(0, current.text.length)
        val newText = current.text.substring(0, cursor) + snippet + current.text.substring(cursor)
        val newCursor = cursor + snippet.length
        _uiState.update {
            it.copy(contentField = TextFieldValue(newText, TextRange(newCursor)))
        }
    }

    fun save() {
        val s = _uiState.value
        if (s.title.isBlank() && s.content.isBlank()) return
        viewModelScope.launch {
            if (currentNoteId == -1L) {
                val newId = syncRepository.createNoteLocal(s.title, s.content, s.category)
                currentNoteId = newId
            } else {
                syncRepository.updateNoteLocal(currentNoteId, s.title, s.content, s.category)
            }
            syncRepository.sync()
        }
    }

    fun deleteNote() {
        viewModelScope.launch {
            if (currentNoteId > 0) {
                syncRepository.deleteNote(currentNoteId)
                syncRepository.sync()
            }
        }
    }
}
