package com.brbrs.nota.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── Legacy glass modifiers (kept for backward compat) ─────────────────────────

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

fun Modifier.glassCardPrimary(
    cornerRadius: Dp = 20.dp,
    tint: Color = CyanPrimary,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(tint.copy(alpha = 0.08f))
        .border(1.dp, tint.copy(alpha = 0.18f), shape)
}

// ── Vinci design language modifiers ──────────────────────────────────────────

/**
 * Standard card — subtle in dark mode, white with green-tinted border in light.
 */
fun Modifier.vinciCard(
    isDark: Boolean,
    cornerRadius: Dp = 20.dp,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return if (isDark) {
        this
            .clip(shape)
            .background(NavySurface)
            .border(1.dp, GlassBorder, shape)
    } else {
        this
            .shadow(
                elevation = 2.dp,
                shape = shape,
                ambientColor = GreenLight.copy(alpha = 0.12f),
                spotColor = GreenLight.copy(alpha = 0.08f),
            )
            .clip(shape)
            .background(LightSurface)
            .border(1.dp, LightBorderSoft, shape)
    }
}

/**
 * Elevated card — used for starred/pinned notes. Green accent border in dark,
 * stronger shadow + medium border in light.
 */
fun Modifier.vinciCardElevated(
    isDark: Boolean,
    cornerRadius: Dp = 20.dp,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return if (isDark) {
        this
            .clip(shape)
            .background(GreenGlow)
            .border(1.dp, GreenBorder, shape)
    } else {
        this
            .shadow(
                elevation = 6.dp,
                shape = shape,
                ambientColor = GreenLight.copy(alpha = 0.20f),
                spotColor = GreenLight.copy(alpha = 0.14f),
            )
            .clip(shape)
            .background(LightSurface)
            .border(1.5.dp, LightBorderMed, shape)
    }
}

/**
 * Chip — used for category filters. Selected state uses accent color.
 */
fun Modifier.vinciChip(
    isDark: Boolean,
    selected: Boolean,
    cornerRadius: Dp = 12.dp,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return if (selected) {
        this
            .clip(shape)
            .background(GreenPrimary.copy(alpha = if (isDark) 0.20f else 0.15f))
            .border(1.dp, GreenPrimary.copy(alpha = if (isDark) 0.50f else 0.40f), shape)
    } else {
        if (isDark) {
            this
                .clip(shape)
                .background(NavySurface)
                .border(1.dp, GlassBorder, shape)
        } else {
            this
                .clip(shape)
                .background(LightSurface2)
                .border(1.dp, LightBorderSoft, shape)
        }
    }
}
