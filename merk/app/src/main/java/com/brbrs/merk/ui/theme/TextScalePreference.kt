package com.brbrs.merk.ui.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import com.brbrs.merk.di.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class TextScale(val label: String, val multiplier: Float) {
    SMALL(label = "Small", multiplier = 0.9f),
    DEFAULT(label = "Default", multiplier = 1.0f),
    LARGE(label = "Large", multiplier = 1.15f),
    EXTRA_LARGE(label = "Extra Large", multiplier = 1.3f);

    companion object {
        fun fromOrdinal(ordinal: Int) = entries.getOrElse(ordinal) { DEFAULT }
    }
}

@Singleton
class TextScalePreference @Inject constructor(
    @SettingsDataStore private val dataStore: DataStore<Preferences>,
) {
    private val TEXT_SCALE = intPreferencesKey("text_scale")

    val textScale: Flow<TextScale> = dataStore.data.map { prefs ->
        TextScale.fromOrdinal(prefs[TEXT_SCALE] ?: TextScale.DEFAULT.ordinal)
    }

    suspend fun setTextScale(scale: TextScale) {
        dataStore.edit { it[TEXT_SCALE] = scale.ordinal }
    }
}
