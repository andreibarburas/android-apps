package com.brbrs.runa.ui.theme

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

// ── Background ────────────────────────────────────────────────────────────────

fun Modifier.runaBackground(isDark: Boolean): Modifier =
    this.background(
        Brush.verticalGradient(
            colors = if (isDark) {
                listOf(DarkBackground, DarkSurface)
            } else {
                listOf(LightBackground, LightSurfaceVariant)
            }
        )
    )

// ── Card styles ───────────────────────────────────────────────────────────────

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
    tint: Color = DarkPrimary,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(tint.copy(alpha = 0.10f))
        .border(1.5.dp, tint.copy(alpha = 0.28f), shape)
}

fun Modifier.lightCard(
    cornerRadius: Dp = 20.dp,
    elevated: Boolean = false,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .shadow(
            elevation = if (elevated) 4.dp else 2.dp,
            shape = shape,
            ambientColor = Color(0x28A8726A),
            spotColor    = Color(0x14A8726A),
        )
        .clip(shape)
        .background(LightSurface)
        .border(1.dp, LightOutline, shape)
}

fun Modifier.lightCardPrimary(
    cornerRadius: Dp = 20.dp,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .shadow(
            elevation = 8.dp, shape = shape,
            ambientColor = Color(0x38A8726A), spotColor = Color(0x1EA8726A),
        )
        .clip(shape)
        .background(LightSurfaceVariant)
        .border(1.5.dp, LightPrimary.copy(alpha = 0.35f), shape)
}

fun Modifier.runaCard(
    isDark: Boolean,
    cornerRadius: Dp = 16.dp,
): Modifier = if (isDark) {
    val shape = RoundedCornerShape(cornerRadius)
    this.clip(shape)
        .background(Color.White.copy(alpha = 0.05f))
        .border(1.dp, Color.White.copy(alpha = 0.09f), shape)
} else {
    this.lightCard(cornerRadius = cornerRadius)
}

fun Modifier.runaCardElevated(
    isDark: Boolean,
    cornerRadius: Dp = 16.dp,
): Modifier = if (isDark) {
    glassCardPrimary(cornerRadius = cornerRadius)
} else {
    lightCardPrimary(cornerRadius = cornerRadius)
}
