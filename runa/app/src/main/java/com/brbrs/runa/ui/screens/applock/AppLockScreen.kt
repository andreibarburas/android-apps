package com.brbrs.runa.ui.screens.applock

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.runa.R
import com.brbrs.runa.auth.AuthRepository
import com.brbrs.runa.auth.StorageMode
import com.brbrs.runa.biometric.BiometricHelper
import com.brbrs.runa.biometric.BiometricResult
import com.brbrs.runa.ui.theme.LocalIsDark
import com.brbrs.runa.ui.theme.runaBackground
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppLockUiState(
    val isReady: Boolean     = false,
    val hasLock: Boolean     = false,
    val hasStorageChoice: Boolean = false,
    val isUnlocked: Boolean  = false,
    val biometricError: String? = null,
)

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val biometricHelper: BiometricHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                authRepository.appLockEnabled,
                authRepository.storageMode,
            ) { lockEnabled, storageMode ->
                AppLockUiState(
                    isReady          = true,
                    hasLock          = lockEnabled,
                    hasStorageChoice = storageMode !is StorageMode.Undecided,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun authenticate(activity: FragmentActivity) {
        biometricHelper.authenticate(
            activity = activity,
            title    = "Unlock your journal",
            onResult = { result ->
                when (result) {
                    is BiometricResult.Success      -> _uiState.value = _uiState.value.copy(isUnlocked = true)
                    is BiometricResult.Error        -> _uiState.value = _uiState.value.copy(biometricError = result.message)
                    is BiometricResult.NotAvailable -> _uiState.value = _uiState.value.copy(isUnlocked = true)
                    is BiometricResult.Cancelled    -> { /* let user tap to retry */ }
                }
            },
        )
    }
}

@Composable
fun AppLockScreen(
    onUnlocked: () -> Unit,
    onNoLock: () -> Unit,
    onLoggedInNoLock: () -> Unit,
    viewModel: AppLockViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark   = LocalIsDark.current
    val activity = LocalContext.current as FragmentActivity

    LaunchedEffect(uiState.isReady) {
        if (!uiState.isReady) return@LaunchedEffect
        when {
            uiState.isUnlocked -> onUnlocked()
            uiState.hasLock    -> viewModel.authenticate(activity)
            uiState.hasStorageChoice -> onLoggedInNoLock()
            else -> onNoLock()
        }
    }

    LaunchedEffect(uiState.isUnlocked) {
        if (uiState.isUnlocked) onUnlocked()
    }

    Box(
        modifier = Modifier.fillMaxSize().runaBackground(isDark),
        contentAlignment = Alignment.Center,
    ) {
        if (uiState.hasLock && !uiState.isUnlocked) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Text(stringResource(R.string.applock_touch_to_unlock_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                uiState.biometricError?.let {
                    Button(onClick = { viewModel.authenticate(activity) }) {
                        Text(stringResource(R.string.applock_unlock_action))
                    }
                }
            }
        }
    }
}
