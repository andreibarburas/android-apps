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
class ViewModePreference @Inject constructor(
    @SettingsDataStore private val dataStore: DataStore<Preferences>,
) {
    private val USE_CARD_VIEW = booleanPreferencesKey("use_card_view")

    val useCardView: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[USE_CARD_VIEW] ?: false
    }

    suspend fun setUseCardView(enabled: Boolean) {
        dataStore.edit { it[USE_CARD_VIEW] = enabled }
    }
}
