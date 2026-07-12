package com.brbrs.blik.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.blik.data.local.ScreenshotEntity
import com.brbrs.blik.data.repository.ScreenshotRepository
import com.brbrs.blik.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val entity: ScreenshotEntity? = null,
    val isAiLoading: Boolean = false,
    val isUploading: Boolean = false,
    val aiError: String? = null,
    val aiModel: String = "CLAUDE",
    val tasksEnabled: Boolean = false,
    val showNoteDialog: Boolean = false,
    val showTagDialog: Boolean = false,
    val showTaskDialog: Boolean = false,
    val showCropScreen: Boolean = false,
    /** Non-null when Android 11+ requires a system delete confirmation dialog. */
    val pendingDeleteSender: android.content.IntentSender? = null,
    /** True when pending delete also wipes the Nextcloud copy. */
    val pendingDeleteIncludesNextcloud: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repo: ScreenshotRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _localPath   = MutableStateFlow<String?>(null)
    private val _isAiLoading = MutableStateFlow(false)
    private val _isUploading = MutableStateFlow(false)
    private val _aiError     = MutableStateFlow<String?>(null)
    private val _dialogs     = MutableStateFlow(Triple(false, false, false)) // note, tag, task
    private val _pendingDelete = MutableStateFlow<Pair<android.content.IntentSender, Boolean>?>(null)
    private val _showCrop    = MutableStateFlow(false)

    val uiState: StateFlow<DetailUiState> = combine(
        _localPath.flatMapLatest { path ->
            if (path == null) flowOf(null)
            else repo.observeAll().map { list -> list.find { it.localPath == path } }
        },
        settings.aiModel,
        settings.tasksEnabled,
    ) { entity, model, tasks ->
        DetailUiState(entity = entity, aiModel = model, tasksEnabled = tasks)
    }.combine(
        combine(_isAiLoading, _isUploading, _aiError) { ai, up, err -> Triple(ai, up, err) }
    ) { state, (ai, up, err) ->
        state.copy(isAiLoading = ai, isUploading = up, aiError = err)
    }.combine(_dialogs) { state, (note, tag, task) ->
        state.copy(showNoteDialog = note, showTagDialog = tag, showTaskDialog = task)
    }.combine(_pendingDelete) { state, pending ->
        state.copy(
            pendingDeleteSender = pending?.first,
            pendingDeleteIncludesNextcloud = pending?.second ?: false,
        )
    }.combine(_showCrop) { state, crop ->
        state.copy(showCropScreen = crop)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DetailUiState())

    fun load(localPath: String) {
        _localPath.value = localPath
    }

    fun runAi() {
        val entity = uiState.value.entity ?: return
        _isAiLoading.value = true
        _aiError.value = null
        viewModelScope.launch {
            repo.runAiAnalysis(entity).fold(
                onSuccess = { _isAiLoading.value = false },
                onFailure = { e -> _isAiLoading.value = false; _aiError.value = e.message },
            )
        }
    }

    fun upload() {
        val entity = uiState.value.entity ?: return
        _isUploading.value = true
        viewModelScope.launch {
            val onConflict = settings.onConflict.firstOrNull() ?: "ASK"
            repo.uploadScreenshot(entity, onConflict)
            _isUploading.value = false
        }
    }

    fun toggleStar() {
        val entity = uiState.value.entity ?: return
        viewModelScope.launch { repo.toggleStar(entity) }
    }

    fun saveNote(note: String) {
        val entity = uiState.value.entity ?: return
        viewModelScope.launch {
            repo.updateNote(entity.localPath, note)
            _dialogs.value = _dialogs.value.copy(first = false)
        }
    }

    fun saveTags(tags: String) {
        val entity = uiState.value.entity ?: return
        viewModelScope.launch {
            repo.updateTags(entity.localPath, tags)
            _dialogs.value = _dialogs.value.copy(second = false)
        }
    }

    fun showNoteDialog()  { _dialogs.value = _dialogs.value.copy(first = true) }
    fun hideNoteDialog()  { _dialogs.value = _dialogs.value.copy(first = false) }
    fun showTagDialog()   { _dialogs.value = _dialogs.value.copy(second = true) }
    fun hideTagDialog()   { _dialogs.value = _dialogs.value.copy(second = false) }
    fun showTaskDialog()  { _dialogs.value = _dialogs.value.copy(third = true) }
    fun hideTaskDialog()  { _dialogs.value = _dialogs.value.copy(third = false) }
    fun dismissAiError()  { _aiError.value = null }
    fun showCropScreen()  { _showCrop.value = true }
    fun hideCropScreen()  { _showCrop.value = false }

    fun deleteLocalOnly(onDone: () -> Unit) {
        val entity = uiState.value.entity ?: return
        viewModelScope.launch {
            val sender = repo.deleteLocalFile(entity)
            if (sender != null) {
                _pendingDelete.value = Pair(sender, false)
                // onDone called after system dialog confirms via onDeleteConfirmed()
            } else {
                onDone()
            }
        }
    }

    fun deleteNextcloudOnly() {
        val entity = uiState.value.entity ?: return
        viewModelScope.launch { repo.deleteFromNextcloud(entity) }
    }

    fun deleteEverywhere(onDone: () -> Unit) {
        val entity = uiState.value.entity ?: return
        viewModelScope.launch {
            val sender = repo.deleteEverywhere(entity)
            if (sender != null) {
                _pendingDelete.value = Pair(sender, true)
            } else {
                onDone()
            }
        }
    }

    /** Called by the screen after the system delete dialog returns RESULT_OK. */
    fun onDeleteConfirmed(onDone: () -> Unit) {
        val entity = uiState.value.entity ?: return
        _pendingDelete.value = null
        viewModelScope.launch { repo.deleteRecord(entity); onDone() }
    }

    /** Called if the user cancelled the system delete dialog. */
    fun onDeleteCancelled() { _pendingDelete.value = null }

    // Used when screenshot is not yet uploaded — just removes local file + record
    fun delete(onDeleted: () -> Unit) = deleteLocalOnly(onDeleted)
}
