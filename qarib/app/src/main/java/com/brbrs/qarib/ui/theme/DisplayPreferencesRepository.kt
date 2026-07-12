package com.brbrs.qarib.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.displayDataStore by preferencesDataStore(name = "qarib_display")

/** Allowed geofence radii, in meters. */
val GEOFENCE_RADIUS_OPTIONS = listOf(100, 250, 500, 1000, 2000, 5000)
const val DEFAULT_GEOFENCE_RADIUS_METERS = 250

data class DisplayPreferences(
    /** Theme mode: "system", "light", or "dark". */
    val themeMode: String = "system",
    /** Text size scale: "small", "default", "large", or "extra_large". */
    val textSize: String = "default",
    /** Global geofence notification radius, in meters. */
    val geofenceRadiusMeters: Int = DEFAULT_GEOFENCE_RADIUS_METERS,
    /** Whether to use DM Serif Display + Inter Tight instead of system fonts. */
    val useCustomFont: Boolean = false,
)

@Singleton
class DisplayPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val THEME_MODE      = stringPreferencesKey("theme_mode")
    private val TEXT_SIZE       = stringPreferencesKey("text_size")
    private val GEOFENCE_RADIUS = intPreferencesKey("geofence_radius_meters")
    private val USE_CUSTOM_FONT = booleanPreferencesKey("use_custom_font")

    val preferences: Flow<DisplayPreferences> = context.displayDataStore.data.map { prefs ->
        DisplayPreferences(
            themeMode           = prefs[THEME_MODE]      ?: "system",
            textSize            = prefs[TEXT_SIZE]        ?: "default",
            geofenceRadiusMeters = prefs[GEOFENCE_RADIUS] ?: DEFAULT_GEOFENCE_RADIUS_METERS,
            useCustomFont       = prefs[USE_CUSTOM_FONT]  ?: false,
        )
    }

    suspend fun setThemeMode(mode: String) {
        context.displayDataStore.edit { it[THEME_MODE] = mode }
    }

    suspend fun setTextSize(size: String) {
        context.displayDataStore.edit { it[TEXT_SIZE] = size }
    }

    suspend fun setGeofenceRadiusMeters(radius: Int) {
        context.displayDataStore.edit { it[GEOFENCE_RADIUS] = radius }
    }

    suspend fun setUseCustomFont(enabled: Boolean) {
        context.displayDataStore.edit { it[USE_CUSTOM_FONT] = enabled }
    }

    /** Returns current preferences as a map suitable for Nextcloud upload. */
    suspend fun toMap(): Map<String, Any> {
        val prefs = preferences.first()
        return mapOf(
            "theme_mode"             to prefs.themeMode,
            "text_size"              to prefs.textSize,
            "geofence_radius_meters" to prefs.geofenceRadiusMeters,
            "use_custom_font"        to prefs.useCustomFont,
        )
    }

    /** Restores preferences from a map downloaded from Nextcloud. */
    suspend fun fromMap(map: Map<String, Any>) {
        context.displayDataStore.edit { prefs ->
            (map["theme_mode"]             as? String)?.let  { prefs[THEME_MODE]      = it }
            (map["text_size"]              as? String)?.let  { prefs[TEXT_SIZE]        = it }
            (map["geofence_radius_meters"] as? Number)?.let  { prefs[GEOFENCE_RADIUS] = it.toInt() }
            (map["use_custom_font"]        as? Boolean)?.let { prefs[USE_CUSTOM_FONT]  = it }
        }
    }
}
