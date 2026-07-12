package com.brbrs.nota.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.nota.auth.AuthRepository
import com.brbrs.nota.biometric.BiometricHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppLockUiState(
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean = false,
    val appLockEnabled: Boolean = false,
    val unlocked: Boolean = false,
)

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    val biometricHelper: BiometricHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                authRepository.session,
                authRepository.appLockEnabled,
            ) { session, appLock ->
                AppLockUiState(
                    isLoading = false,
                    isLoggedIn = session != null,
                    appLockEnabled = appLock,
                    unlocked = false,
                )
            }.collect { _uiState.value = it }
        }
    }

    fun onUnlocked() {
        _uiState.update { it.copy(unlocked = true) }
    }
}
