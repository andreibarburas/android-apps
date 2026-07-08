package com.brbrs.nota.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.fontDataStore by preferencesDataStore(name = "nota_font")

@Singleton
class FontPreference @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val CUSTOM_FONT = booleanPreferencesKey("custom_font_enabled")

    val customFontEnabled: Flow<Boolean> = context.fontDataStore.data
        .map { prefs -> prefs[CUSTOM_FONT] ?: false }

    suspend fun setCustomFontEnabled(enabled: Boolean) {
        context.fontDataStore.edit { it[CUSTOM_FONT] = enabled }
    }
}
