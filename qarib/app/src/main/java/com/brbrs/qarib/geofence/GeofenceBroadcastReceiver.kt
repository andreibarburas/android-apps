package com.brbrs.qarib.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives ENTER/EXIT geofence transitions.
 *
 * ENTER: shows a "you're near <place>" notification, unless the place is
 * currently snoozed (either a timed snooze via [com.brbrs.qarib.data.local.entity.PlaceEntity.snoozedUntil]
 * or "snooze until I leave" via [com.brbrs.qarib.data.local.entity.PlaceEntity.snoozedUntilExit]).
 *
 * EXIT: clears [com.brbrs.qarib.data.local.entity.PlaceEntity.snoozedUntilExit]
 * for the place, so the next ENTER notifies normally.
 */
@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceReceiver"
    }

    @Inject
    lateinit var placeDao: com.brbrs.qarib.data.local.dao.PlaceDao

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null) {
            Log.w(TAG, "onReceive: GeofencingEvent.fromIntent returned null")
            return
        }

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Log.w(TAG, "onReceive: geofencing error: $errorMessage")
            return
        }

        val transition = geofencingEvent.geofenceTransition
        Log.d(TAG, "onReceive: transition=$transition triggeringGeofences=${geofencingEvent.triggeringGeofences?.map { it.requestId }}")

        val triggeringGeofences = geofencingEvent.triggeringGeofences
        if (triggeringGeofences == null) {
            Log.w(TAG, "onReceive: transition but triggeringGeofences is null")
            return
        }

        when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> handleEnter(triggeringGeofences)
            Geofence.GEOFENCE_TRANSITION_EXIT -> handleExit(triggeringGeofences)
            else -> Log.d(TAG, "onReceive: ignoring transition ($transition)")
        }
    }

    private fun handleEnter(triggeringGeofences: List<Geofence>) {
        CoroutineScope(Dispatchers.IO).launch {
            for (geofence in triggeringGeofences) {
                val placeId = geofence.requestId
                val place = placeDao.getById(placeId)
                if (place == null) {
                    Log.w(TAG, "handleEnter: no place found for geofence id $placeId")
                    continue
                }

                val now = System.currentTimeMillis()
                val snoozedByTime = place.snoozedUntil != null && place.snoozedUntil > now
                if (snoozedByTime || place.snoozedUntilExit) {
                    Log.d(TAG, "handleEnter: '${place.name}' is snoozed (until=${place.snoozedUntil}, untilExit=${place.snoozedUntilExit}), skipping notification")
                    continue
                }

                // Clear an expired timed snooze so it doesn't linger.
                if (place.snoozedUntil != null && place.snoozedUntil <= now) {
                    placeDao.setSnoozedUntil(place.id, null)
                }

                Log.d(TAG, "handleEnter: showing notification for '${place.name}' ($placeId)")
                notificationHelper.showNearbyNotification(place.id, place.name, place.address, place.latitude, place.longitude)
            }
        }
    }

    private fun handleExit(triggeringGeofences: List<Geofence>) {
        CoroutineScope(Dispatchers.IO).launch {
            for (geofence in triggeringGeofences) {
                val placeId = geofence.requestId
                val place = placeDao.getById(placeId) ?: continue
                if (place.snoozedUntilExit) {
                    Log.d(TAG, "handleExit: clearing snoozedUntilExit for '${place.name}' ($placeId)")
                    placeDao.setSnoozedUntilExit(place.id, false)
                }
            }
        }
    }
}
