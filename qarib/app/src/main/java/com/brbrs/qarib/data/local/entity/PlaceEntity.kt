package com.brbrs.qarib.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A saved place. The [id] is the stable identifier shared with the
 * Nextcloud JSON sync file — generated locally as a UUID so devices can
 * create places offline without collision.
 */
@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    // Soft-delete flag so deletions can sync across devices before
    // being purged locally.
    val deleted: Boolean = false,
    // Country name, used for grouping/sectioning the list view.
    // Populated from Nominatim's address.country for new places, or
    // derived from the last segment of `address` for places migrated
    // from schema v1.
    val country: String = "",
    // Marked true once the user has visited this place. Visited places
    // stay in their country section but are shown de-emphasized at the
    // bottom of the group.
    val visited: Boolean = false,
    // If true, this place is excluded from geofence registration —
    // the user won't get a "nearby" notification for it.
    val notificationsMuted: Boolean = false,
    // Absolute local file path to a user-attached photo, or empty if
    // none. Files live under context.filesDir/photos/. Synced to the
    // Nextcloud Qarib/photos/ folder, keyed by place id.
    val photoPath: String = "",
    // Per-place geofence notification radius override, in meters. Null
    // means "use the global default" from DisplayPreferencesRepository.
    val geofenceRadiusMeters: Int? = null,
    // Device-local "nearby" notification snooze state — never synced.
    // snoozedUntil: epoch millis; suppress notifications until this time.
    val snoozedUntil: Long? = null,
    // snoozedUntilExit: suppress notifications until the user exits this
    // place's geofence, then auto-clears.
    val snoozedUntilExit: Boolean = false,
)
