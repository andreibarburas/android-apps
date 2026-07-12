package com.brbrs.blik.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.blik.auth.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val serverUrl: String  = "",
    val isLoading: Boolean = false,
    val isPolling: Boolean = false,
    val loggedIn: Boolean  = false,
    val loginUrl: String?  = null,
    val error: String?     = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authManager: AuthManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    fun onServerUrlChanged(url: String) = _uiState.update { it.copy(serverUrl = url, error = null) }

    fun startLoginFlow() {
        val url = _uiState.value.serverUrl.trim()
        if (url.isBlank()) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            authManager.initiateLoginFlow(url).fold(
                onSuccess = { init ->
                    _uiState.update { it.copy(isLoading = false, isPolling = true, loginUrl = init.loginUrl) }
                    authManager.pollLoginFlow(init.pollEndpoint, init.token).fold(
                        onSuccess = { _uiState.update { it.copy(isPolling = false, loggedIn = true) } },
                        onFailure = { e -> _uiState.update { it.copy(isPolling = false, error = e.message) } },
                    )
                },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } },
            )
        }
    }
}
