package com.brbrs.vinci.tasks

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tasksDataStore by preferencesDataStore(name = "vinci_tasks")

@Singleton
class TasksPreference @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val KEY = booleanPreferencesKey("tasks_enabled")

    val enabled: Flow<Boolean> = context.tasksDataStore.data.map { it[KEY] ?: false }

    suspend fun setEnabled(enabled: Boolean) {
        context.tasksDataStore.edit { it[KEY] = enabled }
    }
}
