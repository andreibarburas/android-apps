package com.brbrs.blik.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── Legacy glass modifier (kept for backward compat) ──────────────────────────

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

// ── Blik card and chip modifiers ──────────────────────────────────────────

/**
 * Standard card.
 * Dark: deep navy surface with subtle white border.
 * Light: warm white card with amber-ambient shadow and soft amber border.
 */
fun Modifier.blikCard(
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
                elevation    = 2.dp,
                shape        = shape,
                ambientColor = CyanLight.copy(alpha = 0.14f),
                spotColor    = CyanLight.copy(alpha = 0.09f),
            )
            .clip(shape)
            .background(LightSurface)
            .border(1.dp, LightBorderSoft, shape)
    }
}

/**
 * Elevated card — starred / uploaded screenshots.
 * Dark: orange-tinted background + orange accent border.
 * Light: stronger amber-ambient shadow + medium amber border.
 */
fun Modifier.blikCardElevated(
    isDark: Boolean,
    cornerRadius: Dp = 20.dp,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return if (isDark) {
        this
            .clip(shape)
            .background(OrangeGlow)
            .border(1.dp, OrangeBorder, shape)
    } else {
        this
            .shadow(
                elevation    = 6.dp,
                shape        = shape,
                ambientColor = CyanLight.copy(alpha = 0.22f),
                spotColor    = CyanLight.copy(alpha = 0.16f),
            )
            .clip(shape)
            .background(LightSurface)
            .border(1.5.dp, LightBorderMed, shape)
    }
}

/**
 * Chip — filter chips in the gallery.
 * Selected: orange-tinted fill + orange border.
 * Unselected dark: glass surface.
 * Unselected light: warm surface with amber shadow and soft amber border.
 */
fun Modifier.blikChip(
    isDark: Boolean,
    selected: Boolean,
    cornerRadius: Dp = 12.dp,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return if (selected) {
        this
            .clip(shape)
            .background(CyanPrimary.copy(alpha = if (isDark) 0.20f else 0.15f))
            .border(1.dp, CyanPrimary.copy(alpha = if (isDark) 0.50f else 0.40f), shape)
    } else {
        if (isDark) {
            this
                .clip(shape)
                .background(NavySurface)
                .border(1.dp, GlassBorder, shape)
        } else {
            this
                .shadow(
                    elevation    = 1.dp,
                    shape        = shape,
                    ambientColor = CyanLight.copy(alpha = 0.08f),
                    spotColor    = CyanLight.copy(alpha = 0.05f),
                )
                .clip(shape)
                .background(LightSurface2)
                .border(1.dp, LightBorderSoft, shape)
        }
    }
}
