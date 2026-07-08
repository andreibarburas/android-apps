package com.brbrs.merk.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.brbrs.merk.R

// ── Inter Tight ───────────────────────────────────────────────────────────────
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

// ── DM Serif Display ──────────────────────────────────────────────────────────
val DmSerifDisplayFamily = FontFamily(
    Font(R.font.dm_serif_display_regular, FontWeight.Normal),
)

// ── Paired typography ─────────────────────────────────────────────────────────
// DM Serif Display → screen titles (displayLarge, headlineLarge, headlineMedium)
// Inter Tight      → bookmark titles in list/detail (titleLarge)
// FontFamily.Default → everything else (system font, unchanged)
fun merkTypographyPaired(multiplier: Float = 1.0f) = Typography(
    displayLarge   = TextStyle(fontFamily = DmSerifDisplayFamily,  fontSize = 40.sp * multiplier, fontWeight = FontWeight.Normal, lineHeight = 48.sp * multiplier, letterSpacing = (-0.5).sp),
    headlineLarge  = TextStyle(fontFamily = DmSerifDisplayFamily,  fontSize = 32.sp * multiplier, fontWeight = FontWeight.Normal, lineHeight = 40.sp * multiplier, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = DmSerifDisplayFamily,  fontSize = 24.sp * multiplier, fontWeight = FontWeight.Normal),
    titleLarge     = TextStyle(fontFamily = InterTightFamily,      fontSize = 16.sp * multiplier, fontWeight = FontWeight.SemiBold),
    titleMedium    = TextStyle(fontFamily = FontFamily.Default,    fontSize = 14.sp * multiplier, fontWeight = FontWeight.Medium),
    titleSmall     = TextStyle(fontFamily = FontFamily.Default,    fontSize = 12.sp * multiplier, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    bodyLarge      = TextStyle(fontFamily = FontFamily.Default,    fontSize = 15.sp * multiplier, fontWeight = FontWeight.Normal, lineHeight = 24.sp * multiplier),
    bodyMedium     = TextStyle(fontFamily = FontFamily.Default,    fontSize = 13.sp * multiplier, fontWeight = FontWeight.Normal, lineHeight = 20.sp * multiplier),
    labelLarge     = TextStyle(fontFamily = FontFamily.Default,    fontSize = 12.sp * multiplier, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.sp),
    labelMedium    = TextStyle(fontFamily = FontFamily.Default,    fontSize = 11.sp * multiplier, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelSmall     = TextStyle(fontFamily = FontFamily.Default,    fontSize = 10.sp * multiplier, fontWeight = FontWeight.SemiBold, letterSpacing = 0.12.sp),
)
