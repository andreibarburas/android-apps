package com.brbrs.merk.ui.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.brbrs.merk.di.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FontPreference @Inject constructor(
    @SettingsDataStore private val dataStore: DataStore<Preferences>,
) {
    private val USE_CUSTOM_FONT = booleanPreferencesKey("use_custom_font")

    val useCustomFont: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[USE_CUSTOM_FONT] ?: false
    }

    suspend fun setUseCustomFont(enabled: Boolean) {
        dataStore.edit { it[USE_CUSTOM_FONT] = enabled }
    }
}
