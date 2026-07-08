package com.brbrs.qarib.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.brbrs.qarib.R

// ── System fallback families (default / toggle off) ───────────────────────────
val DisplayFontFamily = FontFamily.Serif      // stands in for Fraunces
val BodyFontFamily    = FontFamily.SansSerif  // stands in for Manrope

// ── Custom families (toggle on) ───────────────────────────────────────────────
val DmSerifDisplayFamily = FontFamily(
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

// ── Base typography (system fonts) ────────────────────────────────────────────
val QaribTypography = buildTypography(
    displayFamily = DisplayFontFamily,
    bodyFamily    = BodyFontFamily,
)

// ── Custom typography (DM Serif Display + Inter Tight) ────────────────────────
val QaribTypographyCustom = buildTypography(
    displayFamily = DmSerifDisplayFamily,
    bodyFamily    = InterTightFamily,
)

private fun buildTypography(
    displayFamily: FontFamily,
    bodyFamily: FontFamily,
) = Typography(
    headlineLarge = TextStyle(
        fontFamily = displayFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = displayFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = displayFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

/** Returns a scaled copy of [base] — applies text size preference. */
fun scaledTypography(base: Typography, scale: Float): Typography {
    fun TextStyle.scaled() = copy(
        fontSize   = fontSize * scale,
        lineHeight = if (lineHeight != TextUnit.Unspecified) lineHeight * scale else lineHeight,
    )
    return Typography(
        headlineLarge  = base.headlineLarge.scaled(),
        headlineMedium = base.headlineMedium.scaled(),
        titleLarge     = base.titleLarge.scaled(),
        titleMedium    = base.titleMedium.scaled(),
        bodyLarge      = base.bodyLarge.scaled(),
        bodyMedium     = base.bodyMedium.scaled(),
        labelLarge     = base.labelLarge.scaled(),
        labelMedium    = base.labelMedium.scaled(),
        labelSmall     = base.labelSmall.scaled(),
        bodySmall      = base.bodySmall.scaled(),
    )
}
