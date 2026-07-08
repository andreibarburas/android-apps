package com.brbrs.blik.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.brbrs.blik.di.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @SettingsDataStore private val dataStore: DataStore<Preferences>,
) {
    companion object {
        val KEY_LOCAL_FOLDER      = stringPreferencesKey("local_screenshot_folder")
        val KEY_LOCAL_FOLDER_DISPLAY = stringPreferencesKey("local_screenshot_folder_display")
        val KEY_REMOTE_FOLDER     = stringPreferencesKey("remote_folder")
        val KEY_AUTO_UPLOAD       = booleanPreferencesKey("auto_upload")
        val KEY_WIFI_ONLY         = booleanPreferencesKey("wifi_only")
        val KEY_CHARGING_ONLY     = booleanPreferencesKey("charging_only")
        val KEY_ON_CONFLICT       = stringPreferencesKey("on_conflict")  // ASK, SKIP, OVERWRITE
        val KEY_CLAUDE_API_KEY    = stringPreferencesKey("claude_api_key")
        val KEY_OPENAI_API_KEY    = stringPreferencesKey("openai_api_key")
        val KEY_AI_MODEL          = stringPreferencesKey("ai_model")     // CLAUDE, OPENAI
        val KEY_AUTO_CATEGORISE   = booleanPreferencesKey("auto_categorize")
        val KEY_AUTO_AI_DESC      = booleanPreferencesKey("auto_ai_desc")
        val KEY_TASKS_ENABLED     = booleanPreferencesKey("tasks_enabled")
        val KEY_USE_INTER_TIGHT   = booleanPreferencesKey("use_inter_tight")
    }

    val localFolder: Flow<String>       = dataStore.data.map { it[KEY_LOCAL_FOLDER] ?: "" }
    val localFolderDisplay: Flow<String> = dataStore.data.map { it[KEY_LOCAL_FOLDER_DISPLAY] ?: "" }
    val remoteFolder: Flow<String>    = dataStore.data.map { it[KEY_REMOTE_FOLDER] ?: "/Screenshots" }
    val autoUpload: Flow<Boolean>     = dataStore.data.map { it[KEY_AUTO_UPLOAD] ?: false }
    val wifiOnly: Flow<Boolean>       = dataStore.data.map { it[KEY_WIFI_ONLY] ?: true }
    val chargingOnly: Flow<Boolean>   = dataStore.data.map { it[KEY_CHARGING_ONLY] ?: false }
    val onConflict: Flow<String>      = dataStore.data.map { it[KEY_ON_CONFLICT] ?: "ASK" }
    val claudeApiKey: Flow<String>    = dataStore.data.map { it[KEY_CLAUDE_API_KEY] ?: "" }
    val openAiApiKey: Flow<String>    = dataStore.data.map { it[KEY_OPENAI_API_KEY] ?: "" }
    val aiModel: Flow<String>         = dataStore.data.map { it[KEY_AI_MODEL] ?: "CLAUDE" }
    val autoCategorize: Flow<Boolean> = dataStore.data.map { it[KEY_AUTO_CATEGORISE] ?: false }
    val autoAiDesc: Flow<Boolean>     = dataStore.data.map { it[KEY_AUTO_AI_DESC] ?: false }
    val tasksEnabled: Flow<Boolean>   = dataStore.data.map { it[KEY_TASKS_ENABLED] ?: false }
    val useInterTight: Flow<Boolean>  = dataStore.data.map { it[KEY_USE_INTER_TIGHT] ?: false }

    suspend fun set(key: Preferences.Key<String>, value: String) {
        dataStore.edit { it[key] = value }
    }

    suspend fun set(key: Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { it[key] = value }
    }
}
