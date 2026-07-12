package com.brbrs.nota.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.nota.auth.AuthRepository
import com.brbrs.nota.auth.NotaSession
import com.brbrs.nota.auth.PollEndpoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.URI
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
                    // Nextcloud returns its internal hostname in the poll endpoint.
                    // We must rewrite it to the URL the user typed, otherwise the
                    // phone can't resolve the internal hostname via its DNS.
                    val baseUrl = serverUrl.trimEnd('/')
                    val originalEndpoint = flow.poll.endpoint
                    // Extract just the path portion after the host
                    val path = try {
                        val uri = java.net.URI(originalEndpoint)
                        uri.rawPath + if (uri.rawQuery != null) "?${uri.rawQuery}" else ""
                    } catch (e: Exception) {
                        // Fallback: strip everything up to the first / after the scheme
                        originalEndpoint.replaceFirst(Regex("^https?://[^/]+"), "")
                    }
                    val rewrittenEndpoint = "$baseUrl$path"
                    val rewrittenPoll = flow.poll.copy(endpoint = rewrittenEndpoint)
                    android.util.Log.d("NotaLogin", "Original poll: $originalEndpoint")
                    android.util.Log.d("NotaLogin", "Rewritten poll: $rewrittenEndpoint")
                    _uiState.update { it.copy(loginUrl = flow.login, isLoading = false) }
                    pollForCredentials(serverUrl, rewrittenPoll)
                }
                .onFailure { e ->
                    val msg = when {
                        e.message?.contains("CLEARTEXT") == true -> "Server requires HTTPS"
                        e.message?.contains("Unable to resolve host") == true -> "Cannot find server — check the URL"
                        e.message?.contains("timeout") == true -> "Connection timed out"
                        e.message?.contains("CERTIFICATE") == true ||
                        e.message?.contains("trust") == true -> "SSL certificate error — check network_security_config"
                        e.message.isNullOrBlank() -> "Cannot reach server (${e.javaClass.simpleName})"
                        else -> "Cannot reach server: ${e.message}"
                    }
                    _uiState.update { it.copy(isLoading = false, error = msg) }
                }
        }
    }

    private fun pollForCredentials(serverUrl: String, poll: PollEndpoint) {
        viewModelScope.launch {
            repeat(60) { // Poll for up to 2 minutes (60 × 2s)
                delay(2_000)
                val result = authRepository.pollLoginFlow(poll)
                result.onSuccess { creds ->
                    authRepository.saveSession(
                        NotaSession(
                            // Use the URL the user typed — Nextcloud may return an
                            // internal hostname that the phone can't resolve
                            serverUrl = serverUrl.trimEnd('/'),
                            username = creds.loginName,
                            appPassword = creds.appPassword,
                        )
                    )
                    _uiState.update { it.copy(loginSuccess = true) }
                    return@launch
                }
                result.onFailure { e ->
                    // "POLL_NOT_READY" means user hasn't approved yet — keep waiting
                    if (e.message?.startsWith("POLL_NOT_READY") == true) return@onFailure
                    // Any other error is a real problem — stop polling
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loginUrl = null,
                            error = "Login failed: ${e.message}"
                        )
                    }
                    return@launch
                }
            }
            _uiState.update { it.copy(error = "Login timed out. Please try again.", loginUrl = null, isLoading = false) }
        }
    }
}
