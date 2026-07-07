package com.brbrs.runa.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.displayDataStore by preferencesDataStore(name = "runa_display")

data class DisplayPreferences(
    val themeMode: String     = "system",
    val textSize: String      = "default",
    val useCustomFont: Boolean = false,
)

@Singleton
class DisplayPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val THEME_MODE      = stringPreferencesKey("theme_mode")
    private val TEXT_SIZE       = stringPreferencesKey("text_size")
    private val USE_CUSTOM_FONT = booleanPreferencesKey("use_custom_font")

    val preferences: Flow<DisplayPreferences> = context.displayDataStore.data.map { prefs ->
        DisplayPreferences(
            themeMode     = prefs[THEME_MODE]      ?: "system",
            textSize      = prefs[TEXT_SIZE]        ?: "default",
            useCustomFont = prefs[USE_CUSTOM_FONT]  ?: false,
        )
    }

    suspend fun setThemeMode(mode: String) {
        context.displayDataStore.edit { it[THEME_MODE] = mode }
    }

    suspend fun setTextSize(size: String) {
        context.displayDataStore.edit { it[TEXT_SIZE] = size }
    }

    suspend fun setUseCustomFont(enabled: Boolean) {
        context.displayDataStore.edit { it[USE_CUSTOM_FONT] = enabled }
    }
}
