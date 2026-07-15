package com.brbrs.qarib.data.remote

import com.brbrs.qarib.data.local.entity.PlaceEntity
import com.brbrs.qarib.data.local.entity.VisitEntity
import com.brbrs.qarib.domain.model.deriveCountryFromAddress
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes/deserializes the places.json sync file.
 *
 * Format (schema v5):
 * {
 *   "version": 5,
 *   "places": [
 *     {
 *       "id": "...",
 *       ...all v4 place fields...,
 *       "visits": [
 *         {
 *           "id": "...",
 *           "visitedAt": 1234567890,
 *           "note": "...",
 *           "hasPhotos": true,
 *           "photoCount": 2,
 *           "createdAt": 1234567890,
 *           "updatedAt": 1234567890,
 *           "deleted": false
 *         }
 *       ]
 *     }
 *   ]
 * }
 *
 * Visit photo paths are never serialized — like place photos,
 * `hasPhotos` signals whether photos exist on Nextcloud under
 * Qarib/visit-photos/{visitId}-{n}.jpg.
 */
object PlacesJsonSerializer {

    private const val SCHEMA_VERSION = 5

    fun serialize(places: List<PlaceEntity>, visitsByPlaceId: Map<String, List<VisitEntity>> = emptyMap()): String {
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

            // Visits for this place.
            val visitsArray = JSONArray()
            visitsByPlaceId[place.id]?.forEach { visit ->
                val v = JSONObject()
                v.put("id", visit.id)
                v.put("visitedAt", visit.visitedAt)
                v.put("note", visit.note)
                v.put("hasPhotos", visit.photoPaths != "[]" && visit.photoPaths.isNotEmpty())
                val photoCount = try { org.json.JSONArray(visit.photoPaths).length() } catch (e: Exception) { 0 }
                v.put("photoCount", photoCount)
                v.put("createdAt", visit.createdAt)
                v.put("updatedAt", visit.updatedAt)
                v.put("deleted", visit.deleted)
                visitsArray.put(v)
            }
            obj.put("visits", visitsArray)

            array.put(obj)
        }
        root.put("places", array)
        return root.toString()
    }

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

    /**
     * Deserializes visit entries from the places.json payload.
     * Returns a flat list of all visits across all places.
     * [localPhotoPathsByVisitId] maps visitId → list of local photo paths.
     */
    fun deserializeVisits(
        json: String,
        localPhotoPathsByVisitId: Map<String, List<String>> = emptyMap(),
    ): List<VisitEntity> {
        if (json.isBlank()) return emptyList()
        val root = JSONObject(json)
        val places = root.optJSONArray("places") ?: JSONArray()
        val result = mutableListOf<VisitEntity>()

        for (i in 0 until places.length()) {
            val placeObj = places.getJSONObject(i)
            val placeId = placeObj.getString("id")
            val visits = placeObj.optJSONArray("visits") ?: continue

            for (j in 0 until visits.length()) {
                val v = visits.getJSONObject(j)
                val visitId = v.getString("id")
                val localPaths = localPhotoPathsByVisitId[visitId] ?: emptyList()
                val photoPathsJson = if (localPaths.isEmpty()) "[]" else {
                    val arr = JSONArray(); localPaths.forEach { arr.put(it) }; arr.toString()
                }
                result.add(
                    VisitEntity(
                        id = visitId,
                        placeId = placeId,
                        visitedAt = v.optLong("visitedAt", System.currentTimeMillis()),
                        note = v.optString("note", ""),
                        photoPaths = photoPathsJson,
                        createdAt = v.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = v.optLong("updatedAt", System.currentTimeMillis()),
                        deleted = v.optBoolean("deleted", false),
                    )
                )
            }
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

    /** Reads visit hasPhotos + photoCount flags from a places.json payload — keyed by visitId. */
    fun readVisitPhotoFlags(json: String): Map<String, Int> {
        if (json.isBlank()) return emptyMap()
        val root = JSONObject(json)
        val places = root.optJSONArray("places") ?: JSONArray()
        val result = mutableMapOf<String, Int>()
        for (i in 0 until places.length()) {
            val placeObj = places.getJSONObject(i)
            val visits = placeObj.optJSONArray("visits") ?: continue
            for (j in 0 until visits.length()) {
                val v = visits.getJSONObject(j)
                if (v.optBoolean("hasPhotos", false)) {
                    result[v.getString("id")] = v.optInt("photoCount", 1)
                }
            }
        }
        return result
    }
}
