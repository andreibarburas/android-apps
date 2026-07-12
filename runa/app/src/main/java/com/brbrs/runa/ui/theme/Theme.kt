package com.brbrs.runa.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
import com.brbrs.runa.R

val LocalIsDark = compositionLocalOf { true }

// ── Font families ─────────────────────────────────────────────────────────────

val DMSerifDisplayFamily = FontFamily(
    Font(R.font.dm_serif_display_regular, FontWeight.Normal),
)

val InterTightFamily = FontFamily(
    Font(R.font.inter_tight_thin,        FontWeight.Thin),
    Font(R.font.inter_tight_extra_light, FontWeight.ExtraLight),
    Font(R.font.inter_tight_light,       FontWeight.Light),
    Font(R.font.inter_tight_regular,     FontWeight.Normal),
    Font(R.font.inter_tight_medium,      FontWeight.Medium),
    Font(R.font.inter_tight_semi_bold,   FontWeight.SemiBold),
    Font(R.font.inter_tight_bold,        FontWeight.Bold),
    Font(R.font.inter_tight_extra_bold,  FontWeight.ExtraBold),
    Font(R.font.inter_tight_black,       FontWeight.Black),
)

// ── Typography ────────────────────────────────────────────────────────────────

fun buildTypography(displayFont: FontFamily, bodyFont: FontFamily) = Typography(
    displayLarge   = TextStyle(fontFamily = displayFont, fontSize = 44.sp, fontWeight = FontWeight.Normal,   lineHeight = 52.sp, letterSpacing = (-0.5).sp),
    headlineLarge  = TextStyle(fontFamily = displayFont, fontSize = 34.sp, fontWeight = FontWeight.Normal,   lineHeight = 42.sp, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = displayFont, fontSize = 26.sp, fontWeight = FontWeight.Normal,   lineHeight = 34.sp, letterSpacing = (-0.2).sp),
    headlineSmall  = TextStyle(fontFamily = displayFont, fontSize = 22.sp, fontWeight = FontWeight.Normal,   lineHeight = 30.sp),
    titleLarge     = TextStyle(fontFamily = bodyFont,    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    titleMedium    = TextStyle(fontFamily = bodyFont,    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.05).sp),
    titleSmall     = TextStyle(fontFamily = bodyFont,    fontSize = 13.sp, fontWeight = FontWeight.Medium),
    bodyLarge      = TextStyle(fontFamily = bodyFont,    fontSize = 15.sp, fontWeight = FontWeight.Normal,   lineHeight = 24.sp),
    bodyMedium     = TextStyle(fontFamily = bodyFont,    fontSize = 13.sp, fontWeight = FontWeight.Normal,   lineHeight = 20.sp),
    bodySmall      = TextStyle(fontFamily = bodyFont,    fontSize = 11.sp, fontWeight = FontWeight.Normal,   lineHeight = 16.sp),
    labelLarge     = TextStyle(fontFamily = bodyFont,    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.06.sp),
    labelMedium    = TextStyle(fontFamily = bodyFont,    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.sp),
    labelSmall     = TextStyle(fontFamily = bodyFont,    fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.12.sp),
)

val AppTypography       = buildTypography(FontFamily.Serif, FontFamily.SansSerif)
val AppTypographyPaired = buildTypography(DMSerifDisplayFamily, InterTightFamily)

fun scaledTypography(base: Typography, scale: Float): Typography {
    fun TextStyle.scaled() = copy(
        fontSize   = fontSize * scale,
        lineHeight = if (lineHeight != TextUnit.Unspecified) lineHeight * scale else lineHeight,
    )
    return Typography(
        displayLarge   = base.displayLarge.scaled(),
        headlineLarge  = base.headlineLarge.scaled(),
        headlineMedium = base.headlineMedium.scaled(),
        headlineSmall  = base.headlineSmall.scaled(),
        titleLarge     = base.titleLarge.scaled(),
        titleMedium    = base.titleMedium.scaled(),
        titleSmall     = base.titleSmall.scaled(),
        bodyLarge      = base.bodyLarge.scaled(),
        bodyMedium     = base.bodyMedium.scaled(),
        bodySmall      = base.bodySmall.scaled(),
        labelLarge     = base.labelLarge.scaled(),
        labelMedium    = base.labelMedium.scaled(),
        labelSmall     = base.labelSmall.scaled(),
    )
}

// ── Color schemes ─────────────────────────────────────────────────────────────

private val DarkColorScheme = darkColorScheme(
    primary              = DarkPrimary,
    onPrimary            = Color(0xFF0F0D0B),
    primaryContainer     = Color(0xFF3A1F1C),
    onPrimaryContainer   = DarkPrimaryVariant,
    secondary            = DarkSecondary,
    onSecondary          = Color(0xFF0F0D0B),
    secondaryContainer   = Color(0xFF2A1F15),
    onSecondaryContainer = Color(0xFFD4B89A),
    tertiary             = DarkAccentHighlight,
    onTertiary           = Color(0xFF0F0D0B),
    background           = DarkBackground,
    onBackground         = DarkInk,
    surface              = DarkSurface,
    onSurface            = DarkInk,
    surfaceVariant       = DarkSurfaceVariant,
    onSurfaceVariant     = DarkInkMuted,
    surfaceContainer     = Color(0xFF211C18),
    outline              = DarkOutline,
    outlineVariant       = Color(0xFF2A2420),
    error                = ErrorDark,
    scrim                = Color(0x99000000),
)

private val LightColorScheme = lightColorScheme(
    primary              = LightPrimary,
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFF2D8D4),
    onPrimaryContainer   = Color(0xFF3B0F0C),
    secondary            = LightSecondary,
    onSecondary          = Color.White,
    secondaryContainer   = LightSurfaceVariant,
    onSecondaryContainer = LightInk,
    tertiary             = LightAccentHighlight,
    onTertiary           = Color.White,
    background           = LightBackground,
    onBackground         = LightInk,
    surface              = LightSurface,
    onSurface            = LightInk,
    surfaceVariant       = LightSurfaceVariant,
    onSurfaceVariant     = LightInkMuted,
    surfaceContainer     = LightSurfaceVariant,
    outline              = LightOutline,
    outlineVariant       = Color(0xFFE8D8D0),
    error                = ErrorLight,
    scrim                = Color(0x66000000),
)

// ── Theme entry point ─────────────────────────────────────────────────────────

enum class ThemeMode { SYSTEM, LIGHT, DARK }

fun textSizeMultiplier(size: String): Float = when (size) {
    "small"       -> 0.85f
    "large"       -> 1.15f
    "extra_large" -> 1.30f
    else          -> 1.00f
}

@Composable
fun RunaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    textScale: Float = 1.0f,
    useCustomFont: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isDarkSystem = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.DARK  -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isDarkSystem
    }

    val baseTypography = if (useCustomFont) AppTypographyPaired else AppTypography
    val typography     = if (textScale == 1.0f) baseTypography else scaledTypography(baseTypography, textScale)

    CompositionLocalProvider(LocalIsDark provides isDark) {
        MaterialTheme(
            colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
            typography  = typography,
            content     = content,
        )
    }
}
