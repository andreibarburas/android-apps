package com.brbrs.qarib.domain.model

import com.brbrs.qarib.data.local.entity.PlaceEntity
import com.brbrs.qarib.ui.theme.PlaceCategory
import java.util.UUID

/**
 * UI/domain representation of a saved place.
 */
data class Place(
    val id: String,
    val name: String,
    val category: PlaceCategory,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val note: String,
    val country: String,
    val visited: Boolean,
    val notificationsMuted: Boolean,
    val photoPath: String,
    val geofenceRadiusMeters: Int?,
    val createdAt: Long,
    val updatedAt: Long
)

fun PlaceEntity.toDomain(): Place = Place(
    id = id,
    name = name,
    category = PlaceCategory.fromStorageKey(category),
    latitude = latitude,
    longitude = longitude,
    address = address,
    note = note,
    country = country,
    visited = visited,
    notificationsMuted = notificationsMuted,
    photoPath = photoPath,
    geofenceRadiusMeters = geofenceRadiusMeters,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Place.toEntity(): PlaceEntity = PlaceEntity(
    id = id,
    name = name,
    category = category.storageKey,
    latitude = latitude,
    longitude = longitude,
    address = address,
    note = note,
    country = country,
    visited = visited,
    notificationsMuted = notificationsMuted,
    photoPath = photoPath,
    geofenceRadiusMeters = geofenceRadiusMeters,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deleted = false
)

/**
 * Creates a new Place with a fresh UUID and current timestamps —
 * used when saving a place from the add-place flow.
 */
fun newPlace(
    name: String,
    category: PlaceCategory,
    latitude: Double,
    longitude: Double,
    address: String,
    note: String,
    country: String = "",
    photoPath: String = "",
    geofenceRadiusMeters: Int? = null,
    id: String = UUID.randomUUID().toString(),
): Place {
    val now = System.currentTimeMillis()
    return Place(
        id = id,
        name = name,
        category = category,
        latitude = latitude,
        longitude = longitude,
        address = address,
        note = note,
        country = country,
        visited = false,
        notificationsMuted = false,
        photoPath = photoPath,
        geofenceRadiusMeters = geofenceRadiusMeters,
        createdAt = now,
        updatedAt = now
    )
}

/**
 * Derives a country name from the last non-empty comma-separated segment
 * of a Nominatim-style address string. Used to backfill `country` for
 * places saved before schema v2 introduced the dedicated field.
 */
fun deriveCountryFromAddress(address: String): String =
    address.split(",")
        .map { it.trim() }
        .lastOrNull { it.isNotEmpty() }
        ?: ""
