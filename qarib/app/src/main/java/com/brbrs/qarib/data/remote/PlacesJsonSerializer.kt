package com.brbrs.qarib.data.remote

import com.brbrs.qarib.data.local.entity.PlaceEntity
import com.brbrs.qarib.domain.model.deriveCountryFromAddress
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes/deserializes the places.json sync file.
 *
 * Format (schema v4):
 * {
 *   "version": 4,
 *   "places": [
 *     {
 *       "id": "...",
 *       "name": "...",
 *       "category": "restaurant",
 *       "latitude": 52.123,
 *       "longitude": 4.456,
 *       "address": "...",
 *       "note": "...",
 *       "country": "Netherlands",
 *       "visited": false,
 *       "notificationsMuted": false,
 *       "hasPhoto": false,
 *       "geofenceRadiusMeters": null,
 *       "createdAt": 1234567890,
 *       "updatedAt": 1234567890,
 *       "deleted": false
 *     }
 *   ]
 * }
 *
 * `photoPath` is a local device file path and is never written to this
 * file. `hasPhoto` instead signals whether a photo exists for this place
 * in the Qarib/photos/ folder on Nextcloud (named "{id}.jpg") — used by
 * PlacesRepository.sync() to know when to upload/download photo files.
 *
 * `geofenceRadiusMeters` is null when the place uses the global default
 * notification radius, or an explicit override in meters.
 *
 * v1-v3 files (no country/visited/notificationsMuted/hasPhoto/
 * geofenceRadiusMeters) are read with country derived from `address` and
 * the new fields defaulting to false/null.
 */
object PlacesJsonSerializer {

    private const val SCHEMA_VERSION = 4

    fun serialize(places: List<PlaceEntity>): String {
        val root = JSONObject()
        root.put("version", SCHEMA_VERSION)

        val array = JSONArray()
        for (place in places) {
            val obj = JSONObject()
            obj.put("id", place.id)
            obj.put("name", place.name)
            obj.put("category", place.category)
            obj.put("latitude", place.latitude)
            obj.put("longitude", place.longitude)
            obj.put("address", place.address)
            obj.put("note", place.note)
            obj.put("country", place.country)
            obj.put("visited", place.visited)
            obj.put("notificationsMuted", place.notificationsMuted)
            obj.put("hasPhoto", place.photoPath.isNotEmpty())
            obj.put("geofenceRadiusMeters", place.geofenceRadiusMeters ?: JSONObject.NULL)
            obj.put("createdAt", place.createdAt)
            obj.put("updatedAt", place.updatedAt)
            obj.put("deleted", place.deleted)
            array.put(obj)
        }
        root.put("places", array)
        return root.toString()
    }

    /**
     * Deserializes remote places, preserving each place's local
     * [PlaceEntity.photoPath] from [localPhotoPaths] (keyed by place id)
     * since the remote JSON never contains device-specific paths.
     */
    fun deserialize(json: String, localPhotoPaths: Map<String, String> = emptyMap()): List<PlaceEntity> {
        if (json.isBlank()) return emptyList()

        val root = JSONObject(json)
        val array = root.optJSONArray("places") ?: JSONArray()
        val result = mutableListOf<PlaceEntity>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val id = obj.getString("id")
            val address = obj.optString("address")
            val country = obj.optString("country").ifBlank { deriveCountryFromAddress(address) }
            result.add(
                PlaceEntity(
                    id = id,
                    name = obj.getString("name"),
                    category = obj.optString("category", "restaurant"),
                    latitude = obj.getDouble("latitude"),
                    longitude = obj.getDouble("longitude"),
                    address = address,
                    note = obj.optString("note"),
                    country = country,
                    visited = obj.optBoolean("visited", false),
                    notificationsMuted = obj.optBoolean("notificationsMuted", false),
                    photoPath = localPhotoPaths[id].orEmpty(),
                    geofenceRadiusMeters = if (obj.has("geofenceRadiusMeters") && !obj.isNull("geofenceRadiusMeters")) {
                        obj.optInt("geofenceRadiusMeters")
                    } else {
                        null
                    },
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                    deleted = obj.optBoolean("deleted", false)
                )
            )
        }
        return result
    }

    /** Reads the `hasPhoto` flag for each place id from a places.json payload. */
    fun readHasPhotoFlags(json: String): Map<String, Boolean> {
        if (json.isBlank()) return emptyMap()
        val root = JSONObject(json)
        val array = root.optJSONArray("places") ?: JSONArray()
        val result = mutableMapOf<String, Boolean>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result[obj.getString("id")] = obj.optBoolean("hasPhoto", false)
        }
        return result
    }
}
