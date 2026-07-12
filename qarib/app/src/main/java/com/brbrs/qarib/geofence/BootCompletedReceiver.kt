package com.brbrs.qarib.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.brbrs.qarib.data.local.dao.PlaceDao
import com.brbrs.qarib.domain.model.toDomain
import com.brbrs.qarib.ui.theme.DisplayPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Re-registers geofences for all saved places after a device reboot,
 * since geofence registrations don't survive a reboot.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var placeDao: PlaceDao

    @Inject
    lateinit var geofenceManager: GeofenceManager

    @Inject
    lateinit var displayPrefs: DisplayPreferencesRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        CoroutineScope(Dispatchers.IO).launch {
            val places = placeDao.getAllIncludingDeleted()
                .filter { !it.deleted && !it.visited && !it.notificationsMuted }
                .map { it.toDomain() }
            val radius = displayPrefs.preferences.first().geofenceRadiusMeters
            geofenceManager.syncGeofences(places, radius)
        }
    }
}
