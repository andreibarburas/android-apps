package com.brbrs.qarib.data.repository

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.core.DataStore
import com.brbrs.qarib.di.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sync bookkeeping. Theme mode and text size live in
 * [com.brbrs.qarib.ui.theme.DisplayPreferencesRepository] instead.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @SettingsDataStore private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
    }

    val lastSyncAt: Flow<Long?> = dataStore.data.map { prefs -> prefs[Keys.LAST_SYNC_AT] }

    suspend fun setLastSyncAt(timestamp: Long) {
        dataStore.edit { prefs -> prefs[Keys.LAST_SYNC_AT] = timestamp }
    }
}
