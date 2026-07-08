package com.brbrs.nota.ui.theme

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
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography

// ── Dark palette — deeper, richer ─────────────────────────────────────────────
val NavyDeep      = Color(0xFF080C18)   // deeper background
val NavyMid       = Color(0xFF0E1525)   // mid layer
val NavySurface   = Color(0xFF161D30)   // surface / cards
val GlassWhite    = Color(0x14FFFFFF)
val GlassBorder   = Color(0x1AFFFFFF)
val GlowGreen     = Color(0x3300C853)   // ambient glow overlay

// ── Green accent ──────────────────────────────────────────────────────────────
val GreenPrimary  = Color(0xFF4CAF78)   // forest green — main accent
val GreenDim      = Color(0x9952A875)
val GreenLight    = Color(0xFF2E7D52)   // deeper for light mode readability
val GreenBorder   = Color(0x334CAF78)   // green-tinted border for dark elevated cards
val GreenGlow     = Color(0x1A4CAF78)   // subtle green glow for card backgrounds

// ── Aliases for backward compat ───────────────────────────────────────────────
val CyanPrimary   = GreenPrimary
val CyanDim       = GreenDim
val CyanLight     = GreenLight

val VioletAccent  = Color(0xFF9370DB)
val SlateText     = Color(0xFF94A3B8)
val SlateTextDim  = Color(0x66CBD5E1)
val White         = Color(0xFFF1F5F9)
val GreenTag      = Color(0xFF34D399)
val ErrorRed      = Color(0xFFFF6B6B)

// ── Light palette — green-tinted, warmer ──────────────────────────────────────
val LightBg           = Color(0xFFEEF5EE)   // soft green-tinted background
val LightSurface      = Color(0xFFF9FCF9)   // near-white with green hint
val LightSurface2     = Color(0xFFE2F0E2)   // category chips, input backgrounds
val LightSurface3     = Color(0xFFD4E8D4)   // deeper surface for emphasis
val LightBorderMed    = Color(0x33388E3C)   // medium green-tinted border
val LightBorderSoft   = Color(0x22388E3C)   // soft green-tinted border
val LightText         = Color(0xFF0F1E0F)   // very dark green-black
val LightTextDim      = Color(0xFF4E6E4E)   // muted green-grey
val LightTextDimmer   = Color(0xFF7A9A7A)   // dimmer text

// ── Custom font families ───────────────────────────────────────────────────────
val InterTightFontFamily = FontFamily(
    Font(com.brbrs.nota.R.font.inter_tight_thin,        FontWeight.Thin),
    Font(com.brbrs.nota.R.font.inter_tight_extra_light, FontWeight.ExtraLight),
    Font(com.brbrs.nota.R.font.inter_tight_light,       FontWeight.Light),
    Font(com.brbrs.nota.R.font.inter_tight_regular,     FontWeight.Normal),
    Font(com.brbrs.nota.R.font.inter_tight_medium,      FontWeight.Medium),
    Font(com.brbrs.nota.R.font.inter_tight_semi_bold,   FontWeight.SemiBold),
    Font(com.brbrs.nota.R.font.inter_tight_bold,        FontWeight.Bold),
    Font(com.brbrs.nota.R.font.inter_tight_extra_bold,  FontWeight.ExtraBold),
    Font(com.brbrs.nota.R.font.inter_tight_black,       FontWeight.Black),
)

val DMSerifDisplayFontFamily = FontFamily(
    Font(com.brbrs.nota.R.font.dm_serif_display_regular, FontWeight.Normal),
)

// ── CompositionLocal for isDark ───────────────────────────────────────────────
val LocalIsDark = compositionLocalOf { true }

// ── CompositionLocal for custom font toggle ───────────────────────────────────
val LocalCustomFontEnabled = compositionLocalOf { false }

// ── Color schemes ─────────────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary          = GreenPrimary,
    onPrimary        = NavyDeep,
    primaryContainer = Color(0x1A4CAF78),
    secondary        = VioletAccent,
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
    primary          = GreenLight,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFD1F0E0),
    secondary        = VioletAccent,
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

// ── Typography — expanded scale ────────────────────────────────────────────────
private val displayFont = FontFamily.Serif
private val bodyFont    = FontFamily.SansSerif

private fun TextStyle.scaled(multiplier: Float): TextStyle = copy(
    fontSize = fontSize * multiplier,
    lineHeight = if (lineHeight != androidx.compose.ui.unit.TextUnit.Unspecified) lineHeight * multiplier else lineHeight,
)

fun notaTypography(scaleMultiplier: Float = 1f, customFont: Boolean = false): Typography {
    // When custom font is on: DM Serif Display for display/headline (titles),
    // Inter Tight for everything else. Default: system Serif/SansSerif.
    val titleFont = if (customFont) DMSerifDisplayFontFamily else displayFont
    val uiFont    = if (customFont) InterTightFontFamily     else bodyFont

    val base = Typography(
        displayLarge   = TextStyle(fontFamily = titleFont, fontSize = 40.sp, fontWeight = FontWeight.Normal,    lineHeight = 48.sp, letterSpacing = (-0.5).sp),
        headlineLarge  = TextStyle(fontFamily = titleFont, fontSize = 32.sp, fontWeight = FontWeight.Normal,    lineHeight = 40.sp, letterSpacing = (-0.3).sp),
        headlineMedium = TextStyle(fontFamily = titleFont, fontSize = 24.sp, fontWeight = FontWeight.Normal),
        titleLarge     = TextStyle(fontFamily = uiFont,    fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        titleMedium    = TextStyle(fontFamily = uiFont,    fontSize = 14.sp, fontWeight = FontWeight.Medium),
        titleSmall     = TextStyle(fontFamily = uiFont,    fontSize = 12.sp, fontWeight = FontWeight.Medium,    letterSpacing = 0.1.sp),
        bodyLarge      = TextStyle(fontFamily = uiFont,    fontSize = 15.sp, fontWeight = FontWeight.Normal,    lineHeight = 24.sp),
        bodyMedium     = TextStyle(fontFamily = uiFont,    fontSize = 13.sp, fontWeight = FontWeight.Normal,    lineHeight = 20.sp),
        bodySmall      = TextStyle(fontFamily = uiFont,    fontSize = 11.sp, fontWeight = FontWeight.Normal,    lineHeight = 16.sp),
        labelLarge     = TextStyle(fontFamily = uiFont,    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,  letterSpacing = 0.5.sp),
        labelMedium    = TextStyle(fontFamily = uiFont,    fontSize = 11.sp, fontWeight = FontWeight.Medium,    letterSpacing = 0.4.sp),
        labelSmall     = TextStyle(fontFamily = uiFont,    fontSize = 10.sp, fontWeight = FontWeight.SemiBold,  letterSpacing = 0.12.sp),
    )
    if (scaleMultiplier == 1f) return base
    return Typography(
        displayLarge   = base.displayLarge.scaled(scaleMultiplier),
        headlineLarge  = base.headlineLarge.scaled(scaleMultiplier),
        headlineMedium = base.headlineMedium.scaled(scaleMultiplier),
        titleLarge     = base.titleLarge.scaled(scaleMultiplier),
        titleMedium    = base.titleMedium.scaled(scaleMultiplier),
        titleSmall     = base.titleSmall.scaled(scaleMultiplier),
        bodyLarge      = base.bodyLarge.scaled(scaleMultiplier),
        bodyMedium     = base.bodyMedium.scaled(scaleMultiplier),
        bodySmall      = base.bodySmall.scaled(scaleMultiplier),
        labelLarge     = base.labelLarge.scaled(scaleMultiplier),
        labelMedium    = base.labelMedium.scaled(scaleMultiplier),
        labelSmall     = base.labelSmall.scaled(scaleMultiplier),
    )
}

// Default (1x, system fonts) typography — kept for any direct references
val NotaTypography = notaTypography(1f)

@Composable
fun NotaTheme(
    isDark: Boolean = true,
    textScaleMultiplier: Float = 1f,
    customFont: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalIsDark provides isDark,
        LocalCustomFontEnabled provides customFont,
    ) {
        MaterialTheme(
            colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
            typography  = notaTypography(textScaleMultiplier, customFont),
            content     = content,
        )
    }
}
