package com.brbrs.merk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Dark palette (deeper, richer) ─────────────────────────────────────────────
val NavyDeep        = Color(0xFF080C18)
val NavyMid         = Color(0xFF0E1525)
val NavySurface     = Color(0xFF161D30)
val GlassWhite      = Color(0x14FFFFFF)
val GlassBorder     = Color(0x1AFFFFFF)
val GlassBorderCyan = Color(0x33E53935)
val CyanPrimary     = Color(0xFFE53935)   // deep crimson — dark mode accent
val CyanDim         = Color(0x99E53935)
val SlateText       = Color(0xFF94A3B8)
val SlateTextDim    = Color(0x66CBD5E1)
val White           = Color(0xFFF1F5F9)
val ErrorRed        = Color(0xFFFF6B6B)
val GlowRed         = Color(0x33D32F2F)   // radial glow / ambient shadow tint — dark mode
val GlowRedLight    = Color(0x15D32F2F)   // softer glow for light mode

// ── Light palette (warm crimson-tinted) ───────────────────────────────────────
val LightBg         = Color(0xFFFDF0F0)
val LightSurface    = Color(0xFFFEF9F9)
val LightSurface2   = Color(0xFFFAE0E0)
val LightSurface3   = Color(0xFFF5CECE)
val LightBorder     = Color(0x1A000000)
val LightBorderMed  = Color(0x33C62828)
val LightBorderSoft = Color(0x22C62828)
val LightText       = Color(0xFF1E0808)
val LightTextDim    = Color(0xFF7A3A3A)
val LightTextDimmer = Color(0xFFADB5C4)
val CyanLight       = Color(0xFFC62828)   // deep crimson — light mode accent

// ── CompositionLocal ──────────────────────────────────────────────────────────
val LocalIsDark = compositionLocalOf { true }

// ── Color schemes ─────────────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary           = CyanPrimary,
    onPrimary         = Color.White,
    primaryContainer  = Color(0x1AE53935),
    secondary         = SlateText,
    onSecondary       = White,
    background        = NavyDeep,
    onBackground      = White,
    surface           = NavyMid,
    onSurface         = White,
    surfaceVariant    = NavySurface,
    onSurfaceVariant  = SlateText,
    outline           = GlassBorder,
    error             = ErrorRed,
)

private val LightColorScheme = lightColorScheme(
    primary           = CyanLight,
    onPrimary         = Color.White,
    primaryContainer  = Color(0xFFFFCDD2),
    secondary         = LightTextDim,
    onSecondary       = Color.White,
    background        = LightBg,
    onBackground      = LightText,
    surface           = LightSurface,
    onSurface         = LightText,
    surfaceVariant    = LightSurface2,
    onSurfaceVariant  = LightTextDim,
    outline           = LightBorderSoft,
    error             = ErrorRed,
)

// ── Typography (expanded scale) ───────────────────────────────────────────────
fun merkTypography(multiplier: Float = 1.0f) = Typography(
    displayLarge   = TextStyle(fontFamily = FontFamily.Serif, fontSize = 40.sp * multiplier, fontWeight = FontWeight.Normal, lineHeight = 48.sp * multiplier, letterSpacing = (-0.5).sp),
    headlineLarge  = TextStyle(fontFamily = FontFamily.Serif, fontSize = 32.sp * multiplier, fontWeight = FontWeight.Normal, lineHeight = 40.sp * multiplier, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 24.sp * multiplier, fontWeight = FontWeight.Normal),
    titleLarge     = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp * multiplier, fontWeight = FontWeight.SemiBold),
    titleMedium    = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp * multiplier, fontWeight = FontWeight.Medium),
    titleSmall     = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp * multiplier, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    bodyLarge      = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp * multiplier, fontWeight = FontWeight.Normal, lineHeight = 24.sp * multiplier),
    bodyMedium     = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp * multiplier, fontWeight = FontWeight.Normal, lineHeight = 20.sp * multiplier),
    labelLarge     = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp * multiplier, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.sp),
    labelMedium    = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp * multiplier, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelSmall     = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 10.sp * multiplier, fontWeight = FontWeight.SemiBold, letterSpacing = 0.12.sp),
)

@Composable
fun BookmarksTheme(
    isDark: Boolean = true,
    textScale: Float = 1.0f,
    useCustomFont: Boolean = false,
    content: @Composable () -> Unit,
) {
    val typography = when {
        useCustomFont -> merkTypographyPaired(textScale)
        else          -> merkTypography(textScale)
    }
    CompositionLocalProvider(LocalIsDark provides isDark) {
        MaterialTheme(
            colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
            typography  = typography,
            content     = content,
        )
    }
}
