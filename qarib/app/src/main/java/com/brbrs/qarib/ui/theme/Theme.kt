package com.brbrs.qarib.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    secondary = LightSecondary,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightInkMuted,
    outline = LightOutline,
    error = ErrorLight,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkBackground,
    secondary = DarkSecondary,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkInkMuted,
    outline = DarkOutline,
    error = ErrorDark,
    onError = DarkBackground
)

/** Whether the active color scheme is dark — used by qaribCard()/qaribChip() etc. */
val LocalIsDark = compositionLocalOf { false }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class TextSizeOption(val storageKey: String, val scale: Float) {
    SMALL("small", 0.9f),
    DEFAULT("default", 1.0f),
    LARGE("large", 1.15f),
    LARGEST("extra_large", 1.3f);

    companion object {
        fun fromStorageKey(key: String): TextSizeOption =
            entries.firstOrNull { it.storageKey == key } ?: DEFAULT
    }
}

fun textSizeMultiplier(textSize: String): Float =
    TextSizeOption.fromStorageKey(textSize).scale

@Composable
fun QaribTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    textScale: Float = 1.0f,
    useCustomFont: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT  -> false
        ThemeMode.DARK   -> true
    }

    val colorScheme = if (darkTheme) DarkColors else LightColors

    val baseTypography = if (useCustomFont) QaribTypographyCustom else QaribTypography
    val typography = if (textScale == 1.0f) baseTypography else scaledTypography(baseTypography, textScale)

    CompositionLocalProvider(LocalIsDark provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = typography,
            content     = content
        )
    }
}

@Composable
fun categoryColor(category: PlaceCategory): Color {
    val dark = LocalIsDark.current
    return when (category) {
        PlaceCategory.RESTAURANT -> if (dark) CategoryRestaurantDark else CategoryRestaurantLight
        PlaceCategory.CAFE       -> if (dark) CategoryCafeDark       else CategoryCafeLight
        PlaceCategory.BAR        -> if (dark) CategoryBarDark        else CategoryBarLight
        PlaceCategory.HOTEL      -> if (dark) CategoryHotelDark      else CategoryHotelLight
        PlaceCategory.ATTRACTION -> if (dark) CategoryAttractionDark else CategoryAttractionLight
        PlaceCategory.MUSEUM     -> if (dark) CategoryMuseumDark     else CategoryMuseumLight
        PlaceCategory.PARK       -> if (dark) CategoryParkDark       else CategoryParkLight
        PlaceCategory.ACTIVITY   -> if (dark) CategoryActivityDark   else CategoryActivityLight
        PlaceCategory.SHOP       -> if (dark) CategoryShopDark       else CategoryShopLight
    }
}
