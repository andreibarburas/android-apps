package com.brbrs.qarib.domain.model

/**
 * A single result from the place name search (Nominatim geocoding).
 */
data class GeocodeResult(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val country: String = "",
)
