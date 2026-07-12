package com.brbrs.blik.ui.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.brbrs.blik.di.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeRepository @Inject constructor(
    @SettingsDataStore private val dataStore: DataStore<Preferences>,
) {
    private val IS_DARK = booleanPreferencesKey("is_dark")
    val isDark: Flow<Boolean> = dataStore.data.map { prefs -> prefs[IS_DARK] ?: true }
    suspend fun setDark(dark: Boolean) { dataStore.edit { it[IS_DARK] = dark } }
}
