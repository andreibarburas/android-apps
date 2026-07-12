package com.brbrs.qarib.ui.screens.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.qarib.auth.AuthRepository
import com.brbrs.qarib.biometric.BiometricHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
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
