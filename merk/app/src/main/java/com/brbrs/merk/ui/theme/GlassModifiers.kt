package com.brbrs.merk.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── Original glass modifiers (kept for AppLock, Login etc.) ──────────────────

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

// ── Merk smart modifiers (crimson-accent) ────────────────────────────────────

/**
 * Regular card:
 *  Dark  — very subtle glass, faint white tint, hairline border
 *  Light — white/blush surface, soft crimson-ambient shadow, soft crimson border
 */
fun Modifier.merkCard(
    isDark: Boolean,
    cornerRadius: Dp = 18.dp,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return if (isDark) {
        this
            .clip(shape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
    } else {
        this
            .shadow(
                elevation    = 2.dp,
                shape        = shape,
                ambientColor = Color(0xFFD32F2F).copy(alpha = 0.10f),
                spotColor    = Color(0xFFD32F2F).copy(alpha = 0.05f),
            )
            .clip(shape)
            .background(LightSurface)
            .border(1.dp, LightBorderSoft, shape)
    }
}

/**
 * Elevated card (starred/pinned bookmarks):
 *  Dark  — crimson-tinted glass + crimson border glow
 *  Light — elevated shadow, crimson-tinted border, LightSurface2 background
 */
fun Modifier.merkCardElevated(
    isDark: Boolean,
    cornerRadius: Dp = 18.dp,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return if (isDark) {
        this
            .clip(shape)
            .background(CyanPrimary.copy(alpha = 0.07f))
            .border(1.dp, CyanPrimary.copy(alpha = 0.22f), shape)
    } else {
        this
            .shadow(
                elevation    = 6.dp,
                shape        = shape,
                ambientColor = Color(0xFFD32F2F).copy(alpha = 0.18f),
                spotColor    = Color(0xFFD32F2F).copy(alpha = 0.10f),
            )
            .clip(shape)
            .background(LightSurface2)
            .border(1.dp, LightBorderMed, shape)
    }
}

/**
 * Tag / category chip:
 *  Dark selected   — crimson tinted bg + crimson border
 *  Dark unselected — very subtle glass, no border
 *  Light selected  — crimson tinted bg + crimson border + shadow
 *  Light unselected — blush white bg + soft crimson border
 */
fun Modifier.merkChip(
    isDark: Boolean,
    selected: Boolean,
    cornerRadius: Dp = 12.dp,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return if (isDark) {
        if (selected) {
            this
                .clip(shape)
                .background(CyanPrimary.copy(alpha = 0.16f))
                .border(1.dp, CyanPrimary.copy(alpha = 0.40f), shape)
        } else {
            this
                .clip(shape)
                .background(Color.White.copy(alpha = 0.06f))
        }
    } else {
        if (selected) {
            this
                .shadow(
                    elevation    = 3.dp,
                    shape        = shape,
                    ambientColor = Color(0xFFD32F2F).copy(alpha = 0.16f),
                    spotColor    = Color(0xFFD32F2F).copy(alpha = 0.08f),
                )
                .clip(shape)
                .background(CyanLight.copy(alpha = 0.10f))
                .border(1.dp, LightBorderMed, shape)
        } else {
            this
                .clip(shape)
                .background(LightSurface)
                .border(1.dp, LightBorderSoft, shape)
        }
    }
}
