package com.brbrs.blik.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import com.brbrs.blik.R

// ── Dark palette — deep, rich ─────────────────────────────────────────────────
val NavyDeep     = Color(0xFF080C18)
val NavyMid      = Color(0xFF0E1525)
val NavySurface  = Color(0xFF161D30)
val GlassWhite   = Color(0x14FFFFFF)
val GlassBorder  = Color(0x1AFFFFFF)
val GlowAmber    = Color(0x33F57C00)

// ── Orange accent ─────────────────────────────────────────────────────────────
val CyanPrimary   = Color(0xFFFF8F00)
val CyanLight     = Color(0xFFF57C00)
val OrangeBorder  = Color(0x33FF8F00)
val OrangeGlow    = Color(0x1AFF8F00)

val SlateText    = Color(0xFF94A3B8)
val White        = Color(0xFFF1F5F9)
val ErrorRed     = Color(0xFFFF6B6B)
val SuccessGreen = Color(0xFF4ADE80)
val WarnYellow   = Color(0xFFFBBF24)

// ── Light palette — warm, amber-tinted ───────────────────────────────────────
val LightBg          = Color(0xFFFDF5EC)
val LightSurface     = Color(0xFFFFFBF7)
val LightSurface2    = Color(0xFFFAE8D0)
val LightSurface3    = Color(0xFFF5D8B0)
val LightBorderMed   = Color(0x33E65100)
val LightBorderSoft  = Color(0x22E65100)
val LightText        = Color(0xFF1E0E00)
val LightTextDim     = Color(0xFF7A4A1A)

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
    displayLarge   = TextStyle(fontFamily = displayFont, fontSize = 40.sp, fontWeight = FontWeight.Normal,   lineHeight = 48.sp, letterSpacing = (-0.5).sp),
    headlineLarge  = TextStyle(fontFamily = displayFont, fontSize = 32.sp, fontWeight = FontWeight.Normal,   lineHeight = 40.sp, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = displayFont, fontSize = 24.sp, fontWeight = FontWeight.Normal,   lineHeight = 32.sp, letterSpacing = (-0.2).sp),
    headlineSmall  = TextStyle(fontFamily = displayFont, fontSize = 20.sp, fontWeight = FontWeight.Normal,   lineHeight = 28.sp),
    titleLarge     = TextStyle(fontFamily = bodyFont,    fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    titleMedium    = TextStyle(fontFamily = bodyFont,    fontSize = 14.sp, fontWeight = FontWeight.Medium,   letterSpacing = (-0.05).sp),
    titleSmall     = TextStyle(fontFamily = bodyFont,    fontSize = 12.sp, fontWeight = FontWeight.Medium),
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

// Default (1x) typography — kept for any direct references
val BlikTypography = AppTypography

private val DarkColorScheme = darkColorScheme(
    primary          = CyanPrimary,
    onPrimary        = NavyDeep,
    primaryContainer = Color(0x1AFF8F00),
    secondary        = SlateText,
    onSecondary      = White,
    background       = NavyDeep,
    onBackground     = White,
    surface          = NavyMid,
    onSurface        = White,
    surfaceVariant   = NavySurface,
    onSurfaceVariant = SlateText,
    outline          = GlassBorder,
    error            = ErrorRed,
)

private val LightColorScheme = lightColorScheme(
    primary          = CyanLight,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFFFE0B2),
    secondary        = LightTextDim,
    onSecondary      = Color.White,
    background       = LightBg,
    onBackground     = LightText,
    surface          = LightSurface,
    onSurface        = LightText,
    surfaceVariant   = LightSurface2,
    onSurfaceVariant = LightTextDim,
    outline          = LightBorderSoft,
    error            = ErrorRed,
)

@Composable
fun BlikTheme(
    isDark: Boolean = true,
    textScaleMultiplier: Float = 1f,
    useCustomFont: Boolean = false,
    content: @Composable () -> Unit,
) {
    val baseTypography = if (useCustomFont) AppTypographyPaired else AppTypography
    val typography     = if (textScaleMultiplier == 1f) baseTypography
                         else scaledTypography(baseTypography, textScaleMultiplier)

    CompositionLocalProvider(LocalIsDark provides isDark) {
        MaterialTheme(
            colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
            typography  = typography,
            content     = content,
        )
    }
}
