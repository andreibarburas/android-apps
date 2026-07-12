package com.brbrs.qarib.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── Background gradient ────────────────────────────────────────────────────────
// Applied once at the root of every screen. Single verticalGradient — no
// second radial layer, which avoids the visible seam bug.
//
// Light: LightBackground (#FAF3F2) → LightSurfaceVariant (#F3E6E4)
// Dark:  DarkBackground  (#1A1715) → DarkSurface        (#252220)

fun Modifier.qaribBackground(isDark: Boolean): Modifier =
    this.background(
        Brush.verticalGradient(
            colors = if (isDark) {
                listOf(DarkBackground, DarkSurface)
            } else {
                listOf(LightBackground, LightSurfaceVariant)
            }
        )
    )

// ── Card levels ───────────────────────────────────────────────────────────────
// Level 0: barely there — background list items.
// Level 1: standard glass card.
// Level 2: elevated — primary action / highlighted cards.
//
// Ported from Vinci's GlassModifiers.kt with the same mechanics, swapped to
// Qarib's rose/sage palette (LightPrimary/DarkPrimary, LightSecondary/DarkSecondary).

/**
 * Standard glass card — for regular list items.
 * Dark: subtle white glass. Light: clean white with soft shadow + warm tint.
 */
fun Modifier.glassCard(
    cornerRadius: Dp = 20.dp,
    bgAlpha: Float = 0.07f,
    borderAlpha: Float = 0.10f,
    tint: Color = Color.White,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(tint.copy(alpha = bgAlpha))
        .border(1.dp, tint.copy(alpha = borderAlpha), shape)
}

/**
 * Elevated card — for highlighted / primary-action states.
 * Dark: rose-tinted glass with glow border.
 * Light: rose-tinted surface with coloured border.
 */
fun Modifier.glassCardPrimary(
    cornerRadius: Dp = 20.dp,
    tint: Color = DarkPrimary,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(tint.copy(alpha = 0.10f))
        .border(1.5.dp, tint.copy(alpha = 0.28f), shape)
}

/**
 * Light mode surface card — solid surface with shadow and optional coloured border.
 * Replaces glassCard in light mode for a cleaner, more material feel.
 */
fun Modifier.lightCard(
    cornerRadius: Dp = 20.dp,
    elevated: Boolean = false,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .shadow(
            elevation = if (elevated) 4.dp else 2.dp,
            shape = shape,
            ambientColor = Color(0x28D6577C),
            spotColor = Color(0x14D6577C),
        )
        .clip(shape)
        // Warm-tinted surface — not pure white
        .background(LightSurface)
        .border(1.dp, LightOutline, shape)
}

/**
 * Light mode elevated card — for primary / highlighted items.
 * Surface + stronger shadow + rose-tinted border accent.
 */
fun Modifier.lightCardPrimary(
    cornerRadius: Dp = 20.dp,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .shadow(
            elevation = 8.dp, shape = shape,
            ambientColor = Color(0x38D6577C), spotColor = Color(0x1ED6577C)
        )
        .clip(shape)
        .background(LightSurfaceVariant)
        .border(1.5.dp, LightPrimary.copy(alpha = 0.35f), shape)
}

/**
 * Smart card — automatically picks the right style for the current mode.
 */
fun Modifier.qaribCard(
    isDark: Boolean,
    cornerRadius: Dp = 20.dp,
): Modifier = if (isDark) {
    val shape = RoundedCornerShape(cornerRadius)
    this.clip(shape)
        .background(Color.White.copy(alpha = 0.06f))
        .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
} else {
    this.lightCard(cornerRadius = cornerRadius)
}

/**
 * Smart elevated card — priority items, both modes.
 */
fun Modifier.qaribCardElevated(
    isDark: Boolean,
    cornerRadius: Dp = 20.dp,
): Modifier = if (isDark) {
    glassCardPrimary(cornerRadius = cornerRadius)
} else {
    lightCardPrimary(cornerRadius = cornerRadius)
}

/**
 * Pill chip — for selection chips, tags, segmented options.
 */
fun Modifier.qaribChip(
    isDark: Boolean,
    selected: Boolean,
    selectedColor: Color = if (isDark) DarkPrimary else LightPrimary,
): Modifier {
    val shape = RoundedCornerShape(50.dp)
    return if (selected) {
        this.clip(shape)
            .background(selectedColor.copy(alpha = if (isDark) 0.22f else 0.14f))
            .border(1.5.dp, selectedColor.copy(alpha = if (isDark) 0.45f else 0.35f), shape)
    } else {
        this.clip(shape)
            .background(
                if (isDark) Color.White.copy(alpha = 0.06f)
                else LightSecondary.copy(alpha = 0.08f)
            )
            .border(
                1.dp,
                if (isDark) Color.White.copy(alpha = 0.10f)
                else LightSecondary.copy(alpha = 0.25f),
                shape
            )
    }
}
