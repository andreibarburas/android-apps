package com.brbrs.qarib.data.remote

import com.brbrs.qarib.domain.model.GeocodeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException

/**
 * Free geocoding via OpenStreetMap's Nominatim public API — no API key required.
 *
 * Usage policy requires a descriptive User-Agent and at most ~1 request/second.
 * This client is only invoked interactively from the add-place search, so
 * that policy is naturally respected.
 */
class NominatimClient(
    private val client: OkHttpClient
) {
    companion object {
        private const val BASE_URL = "https://nominatim.openstreetmap.org/search"
        private const val USER_AGENT = "Qarib-Android-App/0.1 (https://barburas.com)"
    }

    sealed class Result {
        data class Success(val results: List<GeocodeResult>) : Result()
        data class Error(val message: String) : Result()
    }

    suspend fun search(query: String): Result = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Result.Success(emptyList())

        val url = "$BASE_URL?q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
            "&format=json&addressdetails=1&limit=8"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.Error("Server returned ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val results = parseResults(body)
                Result.Success(results)
            }
        } catch (e: IOException) {
            Result.Error(e.message ?: "Network error")
        }
    }

    private fun parseResults(body: String): List<GeocodeResult> {
        val array = JSONArray(body)
        val results = mutableListOf<GeocodeResult>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val displayName = obj.optString("display_name")
            val lat = obj.optString("lat").toDoubleOrNull()
            val lon = obj.optString("lon").toDoubleOrNull()
            if (lat == null || lon == null || displayName.isEmpty()) continue

            results.add(
                GeocodeResult(
                    displayName = displayName,
                    latitude = lat,
                    longitude = lon,
                    address = displayName,
                    country = obj.optJSONObject("address")?.optString("country").orEmpty()
                )
            )
        }
        return results
    }
}
