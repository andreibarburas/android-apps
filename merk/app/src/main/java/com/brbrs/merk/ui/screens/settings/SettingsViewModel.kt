package com.brbrs.merk.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.merk.auth.AuthManager
import com.brbrs.merk.biometric.BiometricHelper
import com.brbrs.merk.data.repository.BookmarkRepository
import com.brbrs.merk.tasks.TasksOrgHelper
import com.brbrs.merk.tasks.TasksPreference
import com.brbrs.merk.ui.theme.FontPreference
import com.brbrs.merk.ui.theme.TextScale
import com.brbrs.merk.ui.theme.TextScalePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String?          = null,
    val username: String?           = null,
    val biometricEnabled: Boolean   = false,
    val biometricAvailable: Boolean = false,
    val tasksEnabled: Boolean       = false,
    val tasksInstalled: Boolean     = false,
    val isSyncing: Boolean          = false,
    val lastSynced: String          = "Never",
    val textScale: TextScale        = TextScale.DEFAULT,
    val useCustomFont: Boolean      = false,
    val loggedOut: Boolean          = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: AuthManager,
    private val repo: BookmarkRepository,
    private val tasksPref: TasksPreference,
    private val textScalePref: TextScalePreference,
    private val fontPref: FontPreference,
) : ViewModel() {

    private val _syncing    = MutableStateFlow(false)
    private val _loggedOut  = MutableStateFlow(false)
    private val _lastSynced = MutableStateFlow("Never")

    // Group the first 6 flows, then combine with textScale separately
    private val _base = combine(
        authManager.credentials,
        authManager.biometricEnabled,
        tasksPref.enabled,
        _syncing,
        _loggedOut,
        _lastSynced,
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        BaseState(
            creds      = arr[0] as? com.brbrs.merk.auth.AuthCredentials,
            bio        = arr[1] as Boolean,
            tasks      = arr[2] as Boolean,
            syncing    = arr[3] as Boolean,
            loggedOut  = arr[4] as Boolean,
            lastSynced = arr[5] as String,
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        _base, textScalePref.textScale, fontPref.useCustomFont,
    ) { base, scale, customFont ->
        SettingsUiState(
            serverUrl          = base.creds?.serverUrl,
            username           = base.creds?.username,
            biometricEnabled   = base.bio,
            biometricAvailable = BiometricHelper.isAvailable(context),
            tasksEnabled       = base.tasks,
            tasksInstalled     = TasksOrgHelper.isInstalled(context),
            isSyncing          = base.syncing,
            lastSynced         = base.lastSynced,
            textScale          = scale,
            useCustomFont      = customFont,
            loggedOut          = base.loggedOut,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private data class BaseState(
        val creds: com.brbrs.merk.auth.AuthCredentials?,
        val bio: Boolean,
        val tasks: Boolean,
        val syncing: Boolean,
        val loggedOut: Boolean,
        val lastSynced: String,
    )

    fun toggleBiometric(enabled: Boolean) {
        viewModelScope.launch { authManager.setBiometricEnabled(enabled) }
    }

    fun toggleTasks(enabled: Boolean) {
        viewModelScope.launch { tasksPref.setEnabled(enabled) }
    }

    fun setTextScale(scale: TextScale) {
        viewModelScope.launch { textScalePref.setTextScale(scale) }
    }

    fun setUseCustomFont(enabled: Boolean) {
        viewModelScope.launch { fontPref.setUseCustomFont(enabled) }
    }

    fun sync() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            repo.sync().onSuccess {
                _lastSynced.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            }
            _syncing.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            repo.clearLocalData()
            authManager.clearCredentials()
            _loggedOut.value = true
        }
    }
}
