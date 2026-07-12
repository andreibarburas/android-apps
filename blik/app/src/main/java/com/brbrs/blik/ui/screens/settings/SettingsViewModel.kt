package com.brbrs.blik.ui.screens.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.brbrs.blik.auth.AuthManager
import com.brbrs.blik.biometric.BiometricHelper
import com.brbrs.blik.data.repository.SettingsRepository
import com.brbrs.blik.tasks.TasksOrgHelper
import com.brbrs.blik.ui.theme.TextScale
import com.brbrs.blik.ui.theme.TextScalePreference
import com.brbrs.blik.worker.UploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String?       = null,
    val username: String?        = null,
    val credentials: com.brbrs.blik.auth.AuthCredentials? = null,
    val localFolder: String      = "",
    val localFolderDisplay: String = "",
    val remoteFolder: String     = "/Screenshots",
    val autoUpload: Boolean      = false,
    val wifiOnly: Boolean        = true,
    val chargingOnly: Boolean    = false,
    val onConflict: String       = "ASK",
    val claudeApiKey: String     = "",
    val openAiApiKey: String     = "",
    val aiModel: String          = "CLAUDE",
    val autoCategorize: Boolean  = false,
    val autoAiDesc: Boolean      = false,
    val biometricEnabled: Boolean   = false,
    val biometricAvailable: Boolean = false,
    val tasksEnabled: Boolean    = false,
    val tasksInstalled: Boolean  = false,
    val textScale: com.brbrs.blik.ui.theme.TextScale = com.brbrs.blik.ui.theme.TextScale.DEFAULT,
    val useInterTight: Boolean   = false,
    val loggedOut: Boolean       = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    app: Application,
    private val authManager: AuthManager,
    private val settings: SettingsRepository,
    private val textScalePreference: TextScalePreference,
) : AndroidViewModel(app) {

    private val ctx = app as Context
    private val _loggedOut = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        authManager.credentials,
        authManager.biometricEnabled,
    ) { creds, bio ->
        Pair(creds, bio)
    }.combine(
        combine(settings.localFolder, settings.localFolderDisplay, settings.remoteFolder) { l, ld, r -> Triple(l, ld, r) }
    ) { (creds, bio), (local, localDisplay, remote) ->
        SettingsUiState(
            serverUrl          = creds?.serverUrl,
            username           = creds?.username,
            credentials        = creds,
            biometricEnabled   = bio,
            biometricAvailable = BiometricHelper.isAvailable(ctx),
            localFolder        = local,
            localFolderDisplay = localDisplay,
            remoteFolder       = remote,
            tasksInstalled     = TasksOrgHelper.isInstalled(ctx),
        )
    }.combine(
        combine(settings.autoUpload, settings.wifiOnly, settings.chargingOnly, settings.onConflict) {
            a, b, c, d -> listOf(a.toString(), b.toString(), c.toString(), d)
        }
    ) { state, flags ->
        state.copy(
            autoUpload   = flags[0] == "true",
            wifiOnly     = flags[1] == "true",
            chargingOnly = flags[2] == "true",
            onConflict   = flags[3],
        )
    }.combine(
        combine(settings.claudeApiKey, settings.openAiApiKey, settings.aiModel) {
            a, b, c -> Triple(a, b, c)
        }
    ) { state, (claude, openai, model) ->
        state.copy(claudeApiKey = claude, openAiApiKey = openai, aiModel = model)
    }.combine(
        combine(settings.autoCategorize, settings.autoAiDesc, settings.tasksEnabled) {
            a, b, c -> Triple(a, b, c)
        }
    ) { state, (cat, desc, tasks) ->
        state.copy(autoCategorize = cat, autoAiDesc = desc, tasksEnabled = tasks)
    }.combine(_loggedOut) { state, out ->
        state.copy(loggedOut = out)
    }.combine(textScalePreference.scale) { state, scale ->
        state.copy(textScale = scale)
    }.combine(settings.useInterTight) { state, interTight ->
        state.copy(useInterTight = interTight)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun setLocalFolder(v: String)     = viewModelScope.launch { settings.set(SettingsRepository.KEY_LOCAL_FOLDER, v) }
    fun setLocalFolderDisplay(v: String) = viewModelScope.launch { settings.set(SettingsRepository.KEY_LOCAL_FOLDER_DISPLAY, v) }
    fun setRemoteFolder(v: String)    = viewModelScope.launch { settings.set(SettingsRepository.KEY_REMOTE_FOLDER, v) }
    fun setAutoUpload(v: Boolean)     = viewModelScope.launch { settings.set(SettingsRepository.KEY_AUTO_UPLOAD, v) }
    fun setWifiOnly(v: Boolean)       = viewModelScope.launch { settings.set(SettingsRepository.KEY_WIFI_ONLY, v) }
    fun setChargingOnly(v: Boolean)   = viewModelScope.launch { settings.set(SettingsRepository.KEY_CHARGING_ONLY, v) }
    fun setOnConflict(v: String)      = viewModelScope.launch { settings.set(SettingsRepository.KEY_ON_CONFLICT, v) }
    fun setClaudeApiKey(v: String)    = viewModelScope.launch { settings.set(SettingsRepository.KEY_CLAUDE_API_KEY, v) }
    fun setOpenAiApiKey(v: String)    = viewModelScope.launch { settings.set(SettingsRepository.KEY_OPENAI_API_KEY, v) }
    fun setAiModel(v: String)         = viewModelScope.launch { settings.set(SettingsRepository.KEY_AI_MODEL, v) }
    fun setAutoCategorize(v: Boolean) = viewModelScope.launch { settings.set(SettingsRepository.KEY_AUTO_CATEGORISE, v) }
    fun setAutoAiDesc(v: Boolean)     = viewModelScope.launch { settings.set(SettingsRepository.KEY_AUTO_AI_DESC, v) }

    fun toggleBiometric(enabled: Boolean) {
        viewModelScope.launch { authManager.setBiometricEnabled(enabled) }
    }

    fun toggleTasks(enabled: Boolean) {
        viewModelScope.launch { settings.set(SettingsRepository.KEY_TASKS_ENABLED, enabled) }
    }

    fun toggleInterTight(enabled: Boolean) {
        viewModelScope.launch { settings.set(SettingsRepository.KEY_USE_INTER_TIGHT, enabled) }
    }

    fun setTextScale(scale: TextScale) {
        viewModelScope.launch { textScalePreference.setScale(scale) }
    }

    fun scheduleAutoUpload() {
        val wifiOnly     = uiState.value.wifiOnly
        val chargingOnly = uiState.value.chargingOnly
        val constraints  = Constraints.Builder().apply {
            if (wifiOnly) setRequiredNetworkType(NetworkType.UNMETERED)
            else          setRequiredNetworkType(NetworkType.CONNECTED)
            if (chargingOnly) setRequiresCharging(true)
        }.build()
        val request = PeriodicWorkRequestBuilder<UploadWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints).build()
        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork("blik_auto_upload", ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun logout() {
        viewModelScope.launch {
            authManager.clearCredentials()
            WorkManager.getInstance(ctx).cancelUniqueWork("blik_auto_upload")
            _loggedOut.value = true
        }
    }
}
