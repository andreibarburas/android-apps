package com.brbrs.blik.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.textScaleDataStore by preferencesDataStore(name = "blik_text_scale")

enum class TextScale(val multiplier: Float, val label: String) {
    SMALL(0.9f, "Small"),
    DEFAULT(1.0f, "Default"),
    LARGE(1.15f, "Large"),
    EXTRA_LARGE(1.3f, "Extra Large"),
}

@Singleton
class TextScalePreference @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val KEY = floatPreferencesKey("text_scale_multiplier")

    val scale: Flow<TextScale> = context.textScaleDataStore.data
        .map { prefs ->
            val multiplier = prefs[KEY] ?: TextScale.DEFAULT.multiplier
            TextScale.values().minByOrNull { kotlin.math.abs(it.multiplier - multiplier) }
                ?: TextScale.DEFAULT
        }

    suspend fun setScale(scale: TextScale) {
        context.textScaleDataStore.edit { it[KEY] = scale.multiplier }
    }
}
