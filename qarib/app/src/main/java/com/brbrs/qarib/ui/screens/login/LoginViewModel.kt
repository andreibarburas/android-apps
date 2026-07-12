package com.brbrs.qarib.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.qarib.auth.AuthRepository
import com.brbrs.qarib.auth.PollEndpoint
import com.brbrs.qarib.auth.QaribSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val serverUrl: String = "",
    val isLoading: Boolean = false,
    val loginUrl: String? = null,
    val error: String? = null,
    val loginSuccess: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onServerUrlChanged(url: String) {
        _uiState.update { it.copy(serverUrl = url, error = null) }
    }

    fun startLogin() {
        val rawUrl = uiState.value.serverUrl.trim()
        val serverUrl = if (rawUrl.startsWith("http")) rawUrl else "https://$rawUrl"

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            authRepository.initiateLoginFlow(serverUrl)
                .onSuccess { flow ->
                    val baseUrl = serverUrl.trimEnd('/')
                    val originalEndpoint = flow.poll.endpoint
                    val path = try {
                        val uri = java.net.URI(originalEndpoint)
                        uri.rawPath + if (uri.rawQuery != null) "?${uri.rawQuery}" else ""
                    } catch (e: Exception) {
                        originalEndpoint.replaceFirst(Regex("^https?://[^/]+"), "")
                    }
                    val rewrittenPoll = flow.poll.copy(endpoint = "$baseUrl$path")
                    _uiState.update { it.copy(loginUrl = flow.login, isLoading = false) }
                    pollForCredentials(serverUrl, rewrittenPoll)
                }
                .onFailure { e ->
                    val msg = when {
                        e.message?.contains("CLEARTEXT") == true -> "Server requires HTTPS"
                        e.message?.contains("Unable to resolve host") == true -> "Cannot find server — check the URL"
                        e.message?.contains("timeout") == true -> "Connection timed out"
                        e.message?.contains("CERTIFICATE") == true ||
                            e.message?.contains("trust") == true -> "SSL certificate error"
                        e.message.isNullOrBlank() -> "Cannot reach server (${e.javaClass.simpleName})"
                        else -> "Cannot reach server: ${e.message}"
                    }
                    _uiState.update { it.copy(isLoading = false, error = msg) }
                }
        }
    }

    private fun pollForCredentials(serverUrl: String, poll: PollEndpoint) {
        viewModelScope.launch {
            repeat(60) {
                delay(2_000)
                val result = authRepository.pollLoginFlow(poll)
                result.onSuccess { creds ->
                    authRepository.saveSession(
                        QaribSession(
                            serverUrl = serverUrl.trimEnd('/'),
                            username = creds.loginName,
                            appPassword = creds.appPassword,
                        )
                    )
                    _uiState.update { it.copy(loginSuccess = true) }
                    return@launch
                }
                result.onFailure { e ->
                    if (e.message?.startsWith("POLL_NOT_READY") == true) return@onFailure
                    _uiState.update {
                        it.copy(isLoading = false, loginUrl = null, error = "Login failed: ${e.message}")
                    }
                    return@launch
                }
            }
            _uiState.update { it.copy(error = "Login timed out. Please try again.", loginUrl = null, isLoading = false) }
        }
    }
}
