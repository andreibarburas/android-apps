package com.brbrs.vinci.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.displayDataStore by preferencesDataStore(name = "vinci_display")

data class DisplayPreferences(
    val isGridView: Boolean = false,
    val showRecentCalls: Boolean = true,
    val showRecentInteractions: Boolean = true,
    /** Country calling code (digits only, no '+') used to normalize phone
     *  numbers that lack one, e.g. "31" for the Netherlands, when opening
     *  WhatsApp/Signal chats. Empty means no normalization is applied. */
    val defaultCountryCode: String = "",
    /** If true, attachments are kept on-device as well as on Nextcloud by
     *  default. If false (default), attachments are Nextcloud-only and
     *  downloaded on demand. */
    val attachmentsKeepLocal: Boolean = false,
    /** Text size scale: "small", "default", "large", or "extra_large". */
    val textSize: String = "default",
    /** If true, use DM Serif Display + Inter Tight font pairing. */
    val useCustomFont: Boolean = false,
)

/** Density multipliers applied to the base typography scale for each text size option. */
fun textSizeMultiplier(textSize: String): Float = when (textSize) {
    "small"      -> 0.9f
    "large"      -> 1.15f
    "extra_large" -> 1.3f
    else          -> 1.0f // "default"
}

@Singleton
class DisplayPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val IS_GRID_VIEW             = booleanPreferencesKey("is_grid_view")
    private val SHOW_RECENT_CALLS        = booleanPreferencesKey("show_recent_calls")
    private val SHOW_RECENT_INTERACTIONS = booleanPreferencesKey("show_recent_interactions")
    private val DEFAULT_COUNTRY_CODE     = stringPreferencesKey("default_country_code")
    private val ATTACHMENTS_KEEP_LOCAL   = booleanPreferencesKey("attachments_keep_local")
    private val TEXT_SIZE                = stringPreferencesKey("text_size")
    private val USE_CUSTOM_FONT          = booleanPreferencesKey("use_custom_font")

    val preferences: Flow<DisplayPreferences> = context.displayDataStore.data.map { prefs ->
        DisplayPreferences(
            isGridView             = prefs[IS_GRID_VIEW]             ?: false,
            showRecentCalls        = prefs[SHOW_RECENT_CALLS]        ?: true,
            showRecentInteractions = prefs[SHOW_RECENT_INTERACTIONS] ?: true,
            defaultCountryCode     = prefs[DEFAULT_COUNTRY_CODE]     ?: "",
            attachmentsKeepLocal   = prefs[ATTACHMENTS_KEEP_LOCAL]   ?: false,
            textSize               = prefs[TEXT_SIZE]                ?: "default",
            useCustomFont          = prefs[USE_CUSTOM_FONT]          ?: false,
        )
    }

    suspend fun setGridView(enabled: Boolean) {
        context.displayDataStore.edit { it[IS_GRID_VIEW] = enabled }
    }

    suspend fun setShowRecentCalls(enabled: Boolean) {
        context.displayDataStore.edit { it[SHOW_RECENT_CALLS] = enabled }
    }

    suspend fun setShowRecentInteractions(enabled: Boolean) {
        context.displayDataStore.edit { it[SHOW_RECENT_INTERACTIONS] = enabled }
    }

    /** Sets the default country calling code (digits only, e.g. "31"). */
    suspend fun setDefaultCountryCode(code: String) {
        val digitsOnly = code.replace(Regex("[^0-9]"), "")
        context.displayDataStore.edit { it[DEFAULT_COUNTRY_CODE] = digitsOnly }
    }

    suspend fun setAttachmentsKeepLocal(enabled: Boolean) {
        context.displayDataStore.edit { it[ATTACHMENTS_KEEP_LOCAL] = enabled }
    }

    suspend fun setTextSize(size: String) {
        context.displayDataStore.edit { it[TEXT_SIZE] = size }
    }

    suspend fun setUseCustomFont(enabled: Boolean) {
        context.displayDataStore.edit { it[USE_CUSTOM_FONT] = enabled }
    }

    /** Returns current preferences as a map suitable for Nextcloud upload. */
    suspend fun toMap(): Map<String, Any> {
        val prefs = preferences.first()
        return mapOf(
            "is_grid_view"              to prefs.isGridView,
            "show_recent_calls"         to prefs.showRecentCalls,
            "show_recent_interactions"  to prefs.showRecentInteractions,
            "default_country_code"      to prefs.defaultCountryCode,
            "attachments_keep_local"    to prefs.attachmentsKeepLocal,
            "text_size"                 to prefs.textSize,
        )
    }

    /** Restores preferences from a map downloaded from Nextcloud. */
    suspend fun fromMap(map: Map<String, Any>) {
        context.displayDataStore.edit { prefs ->
            (map["is_grid_view"] as? Boolean)?.let             { prefs[IS_GRID_VIEW] = it }
            (map["show_recent_calls"] as? Boolean)?.let        { prefs[SHOW_RECENT_CALLS] = it }
            (map["show_recent_interactions"] as? Boolean)?.let { prefs[SHOW_RECENT_INTERACTIONS] = it }
            (map["default_country_code"] as? String)?.let     { prefs[DEFAULT_COUNTRY_CODE] = it }
            (map["attachments_keep_local"] as? Boolean)?.let  { prefs[ATTACHMENTS_KEEP_LOCAL] = it }
            (map["text_size"] as? String)?.let                { prefs[TEXT_SIZE] = it }
        }
    }
}
