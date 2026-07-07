package com.brbrs.runa.ui.screens.login

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.runa.R
import com.brbrs.runa.auth.AuthRepository
import com.brbrs.runa.auth.PollEndpoint
import com.brbrs.runa.auth.RunaSession
import com.brbrs.runa.ui.theme.LocalIsDark
import com.brbrs.runa.ui.theme.glassCard
import com.brbrs.runa.ui.theme.runaBackground
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val serverUrl: String    = "",
    val isLoading: Boolean   = false,
    val loginUrl: String?    = null,
    val error: String?       = null,
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
        val rawUrl    = uiState.value.serverUrl.trim()
        val serverUrl = if (rawUrl.startsWith("http")) rawUrl else "https://$rawUrl"

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.initiateLoginFlow(serverUrl)
                .onSuccess { flow ->
                    val baseUrl          = serverUrl.trimEnd('/')
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
                        e.message?.contains("CLEARTEXT")              == true -> "Server requires HTTPS"
                        e.message?.contains("Unable to resolve host") == true -> "Cannot find server — check the URL"
                        e.message?.contains("timeout")                == true -> "Connection timed out"
                        e.message?.contains("CERTIFICATE")            == true ||
                        e.message?.contains("trust")                  == true -> "SSL certificate error"
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
                        RunaSession(
                            serverUrl   = serverUrl.trimEnd('/'),
                            username    = creds.loginName,
                            appPassword = creds.appPassword,
                        )
                    )
                    _uiState.update { it.copy(loginSuccess = true) }
                    return@launch
                }
                result.onFailure { e ->
                    if (e.message?.startsWith("POLL_NOT_READY") == true) return@onFailure
                    _uiState.update { it.copy(isLoading = false, loginUrl = null, error = "Login failed: ${e.message}") }
                    return@launch
                }
            }
            _uiState.update { it.copy(error = "Login timed out. Please try again.", loginUrl = null, isLoading = false) }
        }
    }
}

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val isDark  = LocalIsDark.current

    LaunchedEffect(uiState.loginSuccess) { if (uiState.loginSuccess) onLoginSuccess() }
    LaunchedEffect(uiState.loginUrl) {
        uiState.loginUrl?.let { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Box(
        modifier = Modifier.fillMaxSize().runaBackground(isDark),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Image(
                painter            = painterResource(id = R.drawable.runa_wordmark),
                contentDescription = "Runa",
                colorFilter        = androidx.compose.ui.graphics.ColorFilter.tint(
                    if (isDark) androidx.compose.ui.graphics.Color.White
                    else MaterialTheme.colorScheme.primary
                ),
                modifier           = Modifier
                    .height(64.dp)
                    .widthIn(max = 220.dp),
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.login_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            OutlinedTextField(
                value = uiState.serverUrl,
                onValueChange = viewModel::onServerUrlChanged,
                label       = { Text(stringResource(R.string.login_server_url_label)) },
                placeholder = { Text(stringResource(R.string.login_server_url_placeholder)) },
                singleLine  = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { viewModel.startLogin() }),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor     = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor   = MaterialTheme.colorScheme.onBackground,
                    cursorColor          = MaterialTheme.colorScheme.primary,
                    focusedLabelColor    = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor  = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
            )

            AnimatedVisibility(uiState.error != null) {
                uiState.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Button(
                onClick  = viewModel::startLogin,
                enabled  = uiState.serverUrl.isNotBlank() && !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text  = if (uiState.loginUrl != null) stringResource(R.string.login_waiting_approval) else stringResource(R.string.login_connect_button),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            if (uiState.loginUrl != null) {
                Text(
                    text  = stringResource(R.string.login_approve_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.login_server_required_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Text(stringResource(R.string.login_server_required_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://nextcloud.com/sign-up/"))) }) {
                    Text(stringResource(R.string.login_find_hosting), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
