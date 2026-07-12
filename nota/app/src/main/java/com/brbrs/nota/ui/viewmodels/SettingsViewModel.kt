package com.brbrs.nota.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.nota.auth.AuthRepository
import com.brbrs.nota.network.SyncRepository
import com.brbrs.nota.tasks.TasksOrgHelper
import com.brbrs.nota.tasks.TasksPreference
import com.brbrs.nota.ui.theme.TextScale
import com.brbrs.nota.ui.theme.TextScalePreference
import com.brbrs.nota.ui.theme.FontPreference
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val appLockEnabled: Boolean = false,
    val lastSynced: String = "Never",
    val loggedOut: Boolean = false,
    val tasksEnabled: Boolean = false,
    val tasksInstalled: Boolean = false,
    val textScale: TextScale = TextScale.DEFAULT,
    val customFontEnabled: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val tasksPref: TasksPreference,
    private val textScalePreference: TextScalePreference,
    private val fontPreference: FontPreference,
) : ViewModel() {

    private val _lastSynced = MutableStateFlow("Never")
    private val _loggedOut  = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            authRepository.session,
            authRepository.appLockEnabled,
            _lastSynced,
            _loggedOut,
            tasksPref.enabled,
        ) { session, appLock, synced, loggedOut, tasks ->
            arrayOf(session, appLock, synced, loggedOut, tasks)
        },
        textScalePreference.scale,
        fontPreference.customFontEnabled,
    ) { arr, textScale, customFont ->
        val session   = arr[0] as com.brbrs.nota.auth.NotaSession?
        val appLock   = arr[1] as Boolean
        val synced    = arr[2] as String
        val loggedOut = arr[3] as Boolean
        val tasks     = arr[4] as Boolean
        SettingsUiState(
            serverUrl          = session?.serverUrl ?: "",
            username           = session?.username  ?: "",
            appLockEnabled     = appLock,
            lastSynced         = synced,
            loggedOut          = loggedOut,
            tasksEnabled       = tasks,
            tasksInstalled     = TasksOrgHelper.isInstalled(context),
            textScale          = textScale,
            customFontEnabled  = customFont,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setAppLock(enabled: Boolean) {
        viewModelScope.launch { authRepository.setAppLockEnabled(enabled) }
    }

    fun toggleTasks(enabled: Boolean) {
        viewModelScope.launch { tasksPref.setEnabled(enabled) }
    }

    fun setTextScale(scale: TextScale) {
        viewModelScope.launch { textScalePreference.setScale(scale) }
    }

    fun setCustomFont(enabled: Boolean) {
        viewModelScope.launch { fontPreference.setCustomFontEnabled(enabled) }
    }

    fun sync() {
        viewModelScope.launch {
            syncRepository.sync()
            _lastSynced.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.clearSession()
            _loggedOut.value = true
        }
    }
}
