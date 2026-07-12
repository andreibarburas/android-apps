package com.brbrs.merk.ui.screens.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.merk.data.local.BookmarkEntity
import com.brbrs.merk.data.local.FolderDao
import com.brbrs.merk.data.local.FolderEntity
import com.brbrs.merk.data.repository.BookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditUiState(
    val id: Long            = -1L,
    val url: String         = "",
    val title: String       = "",
    val description: String = "",
    val tags: List<String>  = emptyList(),
    val tagInput: String    = "",
    val folderId: Long      = -1L,
    val folders: List<FolderEntity> = emptyList(),
    val allTags: List<String>       = emptyList(),
    val isLoading: Boolean  = false,
    val isSaved: Boolean    = false,
    val error: String?      = null,
    // folder creation dialog
    val showNewFolderDialog: Boolean = false,
    val newFolderName: String        = "",
    val isCreatingFolder: Boolean    = false,
    val folderError: String?         = null,
)

@HiltViewModel
class EditBookmarkViewModel @Inject constructor(
    private val repo: BookmarkRepository,
    private val folderDao: FolderDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val bookmarkId: Long = savedStateHandle["bookmarkId"] ?: -1L

    private val _state = MutableStateFlow(EditUiState(id = bookmarkId))
    val uiState: StateFlow<EditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val folders = folderDao.observeAll().first()
            val allTags = repo.getAllTags()
            if (bookmarkId > 0) {
                val bm = repo.getById(bookmarkId)
                _state.update {
                    it.copy(
                        url         = bm?.url ?: "",
                        title       = bm?.title ?: "",
                        description = bm?.description ?: "",
                        tags        = bm?.tags?.split(",")?.filter { t -> t.isNotBlank() } ?: emptyList(),
                        folderId    = bm?.folderId ?: -1L,
                        folders     = folders,
                        allTags     = allTags,
                    )
                }
            } else {
                _state.update { it.copy(folders = folders, allTags = allTags) }
            }
        }
    }

    fun prefill(url: String?, title: String?) {
        _state.update { s ->
            s.copy(
                url   = url?.takeIf { it.isNotBlank() } ?: s.url,
                title = title?.takeIf { it.isNotBlank() } ?: s.title,
            )
        }
    }

    fun onUrlChanged(v: String)         = _state.update { it.copy(url = v) }
    fun onTitleChanged(v: String)       = _state.update { it.copy(title = v) }
    fun onDescriptionChanged(v: String) = _state.update { it.copy(description = v) }
    fun onTagInputChanged(v: String)    = _state.update { it.copy(tagInput = v) }
    fun onFolderSelected(id: Long)      = _state.update { it.copy(folderId = id) }

    fun addTag(tag: String = _state.value.tagInput.trim()) {
        if (tag.isBlank()) return
        _state.update { s -> s.copy(tags = (s.tags + tag).distinct(), tagInput = "") }
    }
    fun removeTag(tag: String) = _state.update { it.copy(tags = it.tags - tag) }

    // ── New folder dialog ─────────────────────────────────────────────────────
    fun showNewFolderDialog()  = _state.update { it.copy(showNewFolderDialog = true, newFolderName = "", folderError = null) }
    fun hideNewFolderDialog()  = _state.update { it.copy(showNewFolderDialog = false) }
    fun onNewFolderNameChanged(v: String) = _state.update { it.copy(newFolderName = v) }

    fun createFolder() {
        val name = _state.value.newFolderName.trim()
        if (name.isBlank()) { _state.update { it.copy(folderError = "Name is required") }; return }
        _state.update { it.copy(isCreatingFolder = true, folderError = null) }
        viewModelScope.launch {
            repo.createFolder(name).fold(
                onSuccess = { folder ->
                    val updated = folderDao.observeAll().first()
                    _state.update { it.copy(
                        isCreatingFolder    = false,
                        showNewFolderDialog = false,
                        folders             = updated,
                        folderId            = folder.id,
                    )}
                },
                onFailure = { e ->
                    _state.update { it.copy(isCreatingFolder = false, folderError = e.message) }
                }
            )
        }
    }

    fun save() {
        val s = _state.value
        if (s.url.isBlank()) { _state.update { it.copy(error = "URL is required") }; return }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val entity = BookmarkEntity(
                id          = if (s.id < 0) -(System.currentTimeMillis()) else s.id,
                url         = s.url.trim(),
                title       = s.title.trim().ifBlank { s.url.trim() },
                description = s.description.trim(),
                tags        = s.tags.joinToString(","),
                folderId    = s.folderId,
                folderName  = s.folders.find { it.id == s.folderId }?.title ?: "",
                faviconUrl  = "",
                addedAt     = System.currentTimeMillis() / 1000,
                isDirty     = true,
            )
            repo.saveLocal(entity)
            _state.update { it.copy(isLoading = false, isSaved = true) }
            repo.sync()  // push to server immediately
        }
    }
}
