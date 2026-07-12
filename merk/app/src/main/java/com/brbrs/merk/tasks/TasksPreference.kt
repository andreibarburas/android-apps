package com.brbrs.merk.tasks

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
class TasksPreference @Inject constructor(
    @SettingsDataStore private val dataStore: DataStore<Preferences>,
) {
    private val KEY = booleanPreferencesKey("tasks_enabled")

    val enabled: Flow<Boolean> = dataStore.data.map { it[KEY] ?: false }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY] = enabled }
    }
}
