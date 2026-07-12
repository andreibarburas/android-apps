package com.brbrs.qarib.data.sync

import android.util.Log
import com.brbrs.qarib.data.repository.PlacesRepository
import com.brbrs.qarib.data.repository.SyncResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debounces background syncs so local edits feel instant while still
 * propagating to Nextcloud automatically, without spamming the server on
 * every keystroke/toggle.
 *
 * - [requestSync] schedules a sync after a short quiet period, restarting
 *   the timer on each call (debounce). Used after local place edits.
 * - [syncNow] runs immediately, cancelling any pending debounced sync.
 *   Used on app foreground/launch and the manual "Sync now" button.
 *
 * All syncs are silent — failures are logged but never surfaced as
 * errors to the user, since most people won't have Nextcloud configured
 * and a background sync failing shouldn't interrupt them. The manual
 * "Sync now" button in Settings still reports its own result via
 * [SettingsViewModel] calling [PlacesRepository.sync] directly.
 */
@Singleton
class SyncScheduler @Inject constructor(
    private val placesRepository: PlacesRepository,
) {
    companion object {
        private const val TAG = "SyncScheduler"
        private const val DEBOUNCE_MILLIS = 2_500L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debounceJob: Job? = null

    private val _isSyncing = MutableStateFlow(false)
    /** True while a background sync is in progress. Purely informational — UI need not block on this. */
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /**
     * Schedules a sync after a short quiet period. Calling this again
     * before the timer fires restarts the wait, so a burst of edits
     * results in a single sync shortly after the last one.
     */
    fun requestSync() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MILLIS)
            runSync()
        }
    }

    /**
     * Runs a sync immediately, cancelling any pending debounced sync.
     * Use on app foreground/launch.
     */
    fun syncNow() {
        debounceJob?.cancel()
        debounceJob = scope.launch { runSync() }
    }

    private suspend fun runSync() {
        _isSyncing.value = true
        try {
            when (val result = placesRepository.sync()) {
                is SyncResult.Success -> Log.d(TAG, "background sync succeeded")
                is SyncResult.NotConnected -> Log.d(TAG, "background sync skipped: not connected")
                is SyncResult.Error -> Log.w(TAG, "background sync failed: ${result.message}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "background sync threw: ${e.message}", e)
        } finally {
            _isSyncing.value = false
        }
    }
}
