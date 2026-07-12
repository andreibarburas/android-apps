package com.brbrs.vinci.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.vinci.auth.AuthRepository
import com.brbrs.vinci.network.WebDavRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FolderPickerUiState(
    val folderName: String = "Vinci",
    val isCreating: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class FolderPickerViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val webDavRepository: WebDavRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FolderPickerUiState())
    val uiState: StateFlow<FolderPickerUiState> = _uiState.asStateFlow()

    fun onFolderNameChanged(name: String) {
        _uiState.update { it.copy(folderName = name, error = null) }
    }

    fun confirm() {
        val folderName = uiState.value.folderName.trim()
        if (folderName.isBlank()) {
            _uiState.update { it.copy(error = "Folder name cannot be empty") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, error = null) }
            try {
                val session = authRepository.session.first()
                if (session == null) {
                    _uiState.update { it.copy(isCreating = false, error = "Not logged in") }
                    return@launch
                }
                val updatedSession = session.copy(vinciFolder = folderName)
                authRepository.saveVinciFolder(folderName)
                webDavRepository.ensureFolderExists(updatedSession)
                _uiState.update { it.copy(isCreating = false, done = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isCreating = false, error = "Could not create folder: ${e.message}")
                }
            }
        }
    }
}
