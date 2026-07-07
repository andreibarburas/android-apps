package com.brbrs.runa.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class LocationResult(
    val displayName: String,
    val shortName: String,
    val latitude: Double,
    val longitude: Double,
)

@Singleton
class LocationRepository @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    suspend fun search(query: String): List<LocationResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val encoded  = java.net.URLEncoder.encode(query, "UTF-8")
            val url      = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=8&addressdetails=1"
            val request  = Request.Builder()
                .url(url)
                .header("User-Agent", "Runa/1.0.0 (Android; journal app)")
                .header("Accept-Language", "en")
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            val arr  = JSONArray(body)
            (0 until arr.length()).mapNotNull { i ->
                val obj     = arr.getJSONObject(i)
                val lat     = obj.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
                val lon     = obj.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
                val display = obj.optString("display_name")
                val address = obj.optJSONObject("address")
                val short   = buildShortName(display, address)
                LocationResult(
                    displayName = display,
                    shortName   = short,
                    latitude    = lat,
                    longitude   = lon,
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Builds a human-readable label in European format.
     *
     * If the place has a proper name (e.g. "Gemeente Pijnacker-Nootdorp",
     * "Albert Heijn", "Central Park") that name leads the label:
     *   "Name, Road HouseNumber, Postcode City, Country"
     *
     * If it's a bare address with no named place:
     *   "Road HouseNumber, Postcode City, Country"
     *
     * Never US-style (house number before road).
     */
    private fun buildShortName(display: String, address: JSONObject?): String {
        if (address == null) return display

        // ── Named place ───────────────────────────────────────────────────────
        // Nominatim returns the most specific named feature under one of these keys.
        val placeName = listOf(
            "amenity", "tourism", "leisure", "shop", "office",
            "building", "historic", "man_made", "natural",
        ).firstNotNullOfOrNull { key ->
            address.optString(key).ifBlank { null }
        }

        // ── Address components (European order) ───────────────────────────────
        val road        = address.optString("road").ifBlank { null }
        val houseNumber = address.optString("house_number").ifBlank { null }
        val postcode    = address.optString("postcode").ifBlank { null }
        val city        = address.optString("city").ifBlank { null }
            ?: address.optString("town").ifBlank { null }
            ?: address.optString("village").ifBlank { null }
            ?: address.optString("municipality").ifBlank { null }
        val country     = address.optString("country").ifBlank { null }

        // "Road HouseNumber" — European style
        val streetPart = when {
            road != null && houseNumber != null -> "$road $houseNumber"
            road != null                        -> road
            else                                -> null
        }

        // "Postcode City"
        val cityPart = when {
            postcode != null && city != null -> "$postcode $city"
            city != null                     -> city
            postcode != null                 -> postcode
            else                             -> null
        }

        val parts = mutableListOf<String>()
        if (placeName != null) parts.add(placeName)
        if (streetPart != null) parts.add(streetPart)
        if (cityPart   != null) parts.add(cityPart)
        if (country    != null) parts.add(country)

        return parts.joinToString(", ").ifBlank { display }
    }
}
