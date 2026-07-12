package com.brbrs.qarib.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.qarib.auth.AuthRepository
import com.brbrs.qarib.data.remote.GpxParser
import com.brbrs.qarib.data.repository.PlacesRepository
import com.brbrs.qarib.data.repository.SettingsRepository
import com.brbrs.qarib.data.repository.SyncResult
import com.brbrs.qarib.data.sync.SyncScheduler
import com.brbrs.qarib.domain.model.newPlace
import com.brbrs.qarib.ui.theme.DisplayPreferencesRepository
import com.brbrs.qarib.ui.theme.PlaceCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val qaribFolder: String = "Qarib",
    val isLoggedIn: Boolean = false,
    val appLockEnabled: Boolean = false,
    val themeMode: String = "system",
    val textSize: String = "default",
    val useCustomFont: Boolean = false,
    val geofenceRadiusMeters: Int = com.brbrs.qarib.ui.theme.DEFAULT_GEOFENCE_RADIUS_METERS,
    val activeGeofenceCount: Int = 0,
    val lastSyncAt: Long? = null,
    val isSyncing: Boolean = false,
    val syncError: String? = null,
    val loggedOut: Boolean = false,
    val importResult: ImportResult? = null,
)

private data class SettingsFlags(
    val loggedOut: Boolean,
    val lastSyncAt: Long?,
    val importResult: ImportResult?,
    val activeGeofenceCount: Int,
)

sealed class ImportResult {
    data class Success(val count: Int) : ImportResult()
    object NoWaypoints : ImportResult()
    data class Error(val message: String) : ImportResult()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val displayPrefs: DisplayPreferencesRepository,
    private val placesRepository: PlacesRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    private val _syncError = MutableStateFlow<String?>(null)
    private val _loggedOut = MutableStateFlow(false)
    private val _importResult = MutableStateFlow<ImportResult?>(null)

    /** Places that would have a geofence registered (not muted, not visited). */
    private val activeGeofenceCount = placesRepository.places.map { places ->
        places.count { !it.notificationsMuted && !it.visited }
    }

    // combine() accepts at most 5 flows directly — pre-combine pairs beyond that.
    private val syncStatus = combine(_isSyncing, _syncError) { syncing, error -> syncing to error }
    private val flags = combine(_loggedOut, settingsRepository.lastSyncAt, _importResult, activeGeofenceCount) { loggedOut, lastSync, importResult, geofenceCount ->
        SettingsFlags(loggedOut, lastSync, importResult, geofenceCount)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        authRepository.session,
        authRepository.appLockEnabled,
        displayPrefs.preferences,
        syncStatus,
        flags,
    ) { session, appLock, display, sync, flags ->
        val (isSyncing, syncError) = sync
        SettingsUiState(
            serverUrl = session?.serverUrl ?: "",
            username = session?.username ?: "",
            qaribFolder = session?.qaribFolder ?: "Qarib",
            isLoggedIn = session != null,
            appLockEnabled = appLock,
            themeMode = display.themeMode,
            textSize = display.textSize,
            useCustomFont = display.useCustomFont,
            geofenceRadiusMeters = display.geofenceRadiusMeters,
            activeGeofenceCount = flags.activeGeofenceCount,
            lastSyncAt = flags.lastSyncAt,
            isSyncing = isSyncing,
            syncError = syncError,
            loggedOut = flags.loggedOut,
            importResult = flags.importResult,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setThemeMode(mode: String) {
        viewModelScope.launch { displayPrefs.setThemeMode(mode) }
    }

    fun setTextSize(size: String) {
        viewModelScope.launch { displayPrefs.setTextSize(size) }
    }

    fun setUseCustomFont(enabled: Boolean) {
        viewModelScope.launch { displayPrefs.setUseCustomFont(enabled) }
    }

    fun setGeofenceRadius(radiusMeters: Int) {
        viewModelScope.launch { displayPrefs.setGeofenceRadiusMeters(radiusMeters) }
    }

    fun setAppLock(enabled: Boolean) {
        viewModelScope.launch { authRepository.setAppLockEnabled(enabled) }
    }

    fun syncNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            when (val result = placesRepository.sync()) {
                is SyncResult.Success -> _isSyncing.value = false
                is SyncResult.NotConnected -> _isSyncing.value = false
                is SyncResult.Error -> {
                    _isSyncing.value = false
                    _syncError.value = result.message
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.clearSession()
            _loggedOut.value = true
        }
    }

    /** Parses the GPX file at [uri] and imports its waypoints as new places. */
    fun importFromGpx(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val waypoints = context.contentResolver.openInputStream(uri)?.use { stream ->
                    GpxParser.parseWaypoints(stream)
                } ?: emptyList()

                if (waypoints.isEmpty()) {
                    _importResult.value = ImportResult.NoWaypoints
                    return@launch
                }

                val places = waypoints.map { wpt ->
                    newPlace(
                        name = wpt.name,
                        category = PlaceCategory.ATTRACTION,
                        latitude = wpt.latitude,
                        longitude = wpt.longitude,
                        address = "",
                        note = wpt.description,
                    )
                }

                val count = placesRepository.importPlaces(places)
                if (count > 0) syncScheduler.requestSync()
                _importResult.value = ImportResult.Success(count)
            } catch (e: Exception) {
                _importResult.value = ImportResult.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun dismissImportResult() {
        _importResult.value = null
    }
}
