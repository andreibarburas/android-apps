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

private val Context.themeDataStore by preferencesDataStore(name = "nota_theme")

@Singleton
class ThemeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val IS_DARK = booleanPreferencesKey("is_dark")

    val isDark: Flow<Boolean> = context.themeDataStore.data
        .map { prefs -> prefs[IS_DARK] ?: true }

    suspend fun setDark(dark: Boolean) {
        context.themeDataStore.edit { it[IS_DARK] = dark }
    }
}
