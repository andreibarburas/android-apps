package com.brbrs.vinci.ui.theme

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

// ── Card levels ───────────────────────────────────────────────────────────────
// Level 0: barely there — background contact cards
// Level 1: standard glass card
// Level 2: elevated — starred, priority, primary action cards
// Level 3: hero — contact detail header, log screen header

/**
 * Standard glass card — for regular contact list items.
 * Dark: subtle white glass. Light: clean white with soft shadow + lavender tint.
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
 * Elevated card — for starred contacts, follow-ups, active states.
 * Dark: purple-tinted glass with glow border.
 * Light: lavender-tinted surface with coloured border.
 */
fun Modifier.glassCardPrimary(
    cornerRadius: Dp = 20.dp,
    tint: Color = CyanPrimary,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(tint.copy(alpha = 0.10f))
        .border(1.5.dp, tint.copy(alpha = 0.28f), shape)
}

/**
 * Light mode surface card — solid white with shadow and optional coloured border.
 * Replaces glassCard in light mode for a cleaner, more material feel.
 */
fun Modifier.lightCard(
    cornerRadius: Dp = 20.dp,
    elevated: Boolean = false,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .shadow(
            elevation    = if (elevated) 4.dp else 2.dp,
            shape        = shape,
            ambientColor = Color(0x289C27B0),
            spotColor    = Color(0x149C27B0),
        )
        .clip(shape)
        // Lavender-tinted surface — not pure white
        .background(LightSurface)
        .border(1.dp, LightBorderSoft, shape)
}

/**
 * Light mode elevated card — for starred, priority items.
 * White surface + stronger shadow + purple-tinted left border accent.
 */
fun Modifier.lightCardPrimary(
    cornerRadius: Dp = 20.dp,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .shadow(elevation = 8.dp, shape = shape,
            ambientColor = Color(0x389C27B0), spotColor = Color(0x1E9C27B0))
        .clip(shape)
        // Tinted lavender surface for elevated cards
        .background(LightSurface2)
        .border(1.5.dp, PurpleLight.copy(alpha = 0.35f), shape)
}

/**
 * Smart card — automatically picks the right style for the current mode.
 */
fun Modifier.vinciCard(
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
fun Modifier.vinciCardElevated(
    isDark: Boolean,
    cornerRadius: Dp = 20.dp,
): Modifier = if (isDark) {
    glassCardPrimary(cornerRadius = cornerRadius)
} else {
    lightCardPrimary(cornerRadius = cornerRadius)
}

/**
 * Pill chip — for reason chips, outcome chips, tags.
 */
fun Modifier.vinciChip(
    isDark: Boolean,
    selected: Boolean,
    selectedColor: Color = CyanPrimary,
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
                else Color(0xFF9C27B0).copy(alpha = 0.08f)
            )
            .border(1.dp,
                if (isDark) Color.White.copy(alpha = 0.10f)
                else Color(0xFF9C27B0).copy(alpha = 0.25f),
                shape)
    }
}
