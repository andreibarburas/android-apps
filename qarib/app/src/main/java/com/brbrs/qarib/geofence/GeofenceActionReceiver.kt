package com.brbrs.qarib.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles notification action buttons from [NotificationHelper]'s
 * "nearby" notification: snoozing future notifications for a place.
 */
@AndroidEntryPoint
class GeofenceActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceActionReceiver"

        const val ACTION_SNOOZE_1_HOUR = "com.brbrs.qarib.action.SNOOZE_1_HOUR"
        const val ACTION_SNOOZE_UNTIL_EXIT = "com.brbrs.qarib.action.SNOOZE_UNTIL_EXIT"

        const val EXTRA_PLACE_ID = "place_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        private const val ONE_HOUR_MILLIS = 60 * 60 * 1000L
    }

    @Inject
    lateinit var placeDao: com.brbrs.qarib.data.local.dao.PlaceDao

    override fun onReceive(context: Context, intent: Intent) {
        val placeId = intent.getStringExtra(EXTRA_PLACE_ID)
        if (placeId == null) {
            Log.w(TAG, "onReceive: missing placeId extra")
            return
        }

        // Dismiss the notification immediately regardless of outcome.
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }

        when (intent.action) {
            ACTION_SNOOZE_1_HOUR -> {
                Log.d(TAG, "onReceive: snoozing '$placeId' for 1 hour")
                CoroutineScope(Dispatchers.IO).launch {
                    placeDao.setSnoozedUntil(placeId, System.currentTimeMillis() + ONE_HOUR_MILLIS)
                }
            }
            ACTION_SNOOZE_UNTIL_EXIT -> {
                Log.d(TAG, "onReceive: snoozing '$placeId' until exit")
                CoroutineScope(Dispatchers.IO).launch {
                    placeDao.setSnoozedUntilExit(placeId, true)
                }
            }
            else -> Log.w(TAG, "onReceive: unknown action ${intent.action}")
        }
    }
}
