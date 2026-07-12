package com.brbrs.qarib.geofence

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.brbrs.qarib.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_ID = "qarib_geofence"
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_geofence_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_geofence_description)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Shows a "<place> is nearby" notification with the place's address
     * and three actions: directions, snooze for 1 hour, and snooze until
     * the user leaves the area. Caller must ensure POST_NOTIFICATIONS is
     * granted on Android 13+ before calling this.
     */
    fun showNearbyNotification(placeId: String, placeName: String, placeAddress: String, latitude: Double, longitude: Double) {
        val notificationId = placeId.hashCode()

        val directionsIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(placeName)})")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snooze1HourIntent = actionPendingIntent(
            requestCode = notificationId * 10 + 1,
            action = GeofenceActionReceiver.ACTION_SNOOZE_1_HOUR,
            placeId = placeId,
            notificationId = notificationId,
        )

        val snoozeUntilExitIntent = actionPendingIntent(
            requestCode = notificationId * 10 + 2,
            action = GeofenceActionReceiver.ACTION_SNOOZE_UNTIL_EXIT,
            placeId = placeId,
            notificationId = notificationId,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_nearby_title, placeName))
            .setContentText(context.getString(R.string.notification_nearby_body, placeName, placeAddress))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notification_nearby_body, placeName, placeAddress)))
            .setContentIntent(directionsIntent)
            .addAction(R.drawable.ic_notification, context.getString(R.string.notification_action_directions, placeName), directionsIntent)
            .addAction(R.drawable.ic_notification, context.getString(R.string.notification_action_snooze_1_hour), snooze1HourIntent)
            .addAction(R.drawable.ic_notification, context.getString(R.string.notification_action_snooze_until_exit), snoozeUntilExitIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun actionPendingIntent(requestCode: Int, action: String, placeId: String, notificationId: Int): PendingIntent {
        val intent = Intent(context, GeofenceActionReceiver::class.java).apply {
            this.action = action
            putExtra(GeofenceActionReceiver.EXTRA_PLACE_ID, placeId)
            putExtra(GeofenceActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
