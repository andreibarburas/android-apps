package com.brbrs.qarib.geofence

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.brbrs.qarib.domain.model.Place
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers and unregisters geofences for saved places using the
 * GeofencingClient. Each place gets one ENTER-triggered geofence, sized
 * by the global radius from [com.brbrs.qarib.ui.theme.DisplayPreferencesRepository].
 */
@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "GeofenceManager"
    }

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Replaces all registered geofences with one per place in [places].
     * Each place uses its own [Place.geofenceRadiusMeters] override if
     * set, otherwise [defaultRadiusMeters]. Geofence IDs are the place's
     * UUID, so re-registering naturally updates existing ones.
     *
     * No-op if location permission isn't granted.
     */
    fun syncGeofences(places: List<Place>, defaultRadiusMeters: Int) {
        if (!hasLocationPermission()) {
            Log.w(TAG, "syncGeofences: skipped, location permission not granted")
            return
        }

        Log.d(TAG, "syncGeofences: removing existing geofences before registering ${places.size} place(s)")

        geofencingClient.removeGeofences(geofencePendingIntent)
            .addOnSuccessListener {
                Log.d(TAG, "syncGeofences: removeGeofences succeeded")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "syncGeofences: removeGeofences failed: ${e.message}", e)
            }
            .addOnCompleteListener {
                if (places.isEmpty()) {
                    Log.d(TAG, "syncGeofences: no eligible places, nothing to register")
                    return@addOnCompleteListener
                }

                val geofences = places.map { place ->
                    val radius = (place.geofenceRadiusMeters ?: defaultRadiusMeters).toFloat()
                    Log.d(TAG, "syncGeofences: registering '${place.name}' (${place.id}) radius=${radius}m at ${place.latitude},${place.longitude}")
                    Geofence.Builder()
                        .setRequestId(place.id)
                        .setCircularRegion(place.latitude, place.longitude, radius)
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                        .build()
                }

                val request = GeofencingRequest.Builder()
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                    .addGeofences(geofences)
                    .build()

                try {
                    geofencingClient.addGeofences(request, geofencePendingIntent)
                        .addOnSuccessListener {
                            Log.d(TAG, "syncGeofences: addGeofences succeeded for ${geofences.size} geofence(s)")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "syncGeofences: addGeofences FAILED: ${e.message}", e)
                        }
                } catch (e: SecurityException) {
                    // Permission revoked between the check above and this call.
                    Log.e(TAG, "syncGeofences: addGeofences threw SecurityException: ${e.message}", e)
                }
            }
    }

    /** Removes all registered geofences, e.g. when the user signs out. */
    fun clearGeofences() {
        geofencingClient.removeGeofences(geofencePendingIntent)
            .addOnSuccessListener {
                Log.d(TAG, "clearGeofences: succeeded")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "clearGeofences: failed: ${e.message}", e)
            }
    }
}
