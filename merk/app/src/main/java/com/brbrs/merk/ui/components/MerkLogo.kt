package com.brbrs.merk.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.brbrs.merk.R
import com.brbrs.merk.ui.theme.LocalIsDark

@Composable
fun MerkLogo(modifier: Modifier = Modifier) {
    val isDark = LocalIsDark.current
    Image(
        painter     = painterResource(id = R.drawable.merk_logo),
        contentDescription = "Merk",
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(if (isDark) Color.White else Color(0xFF1E293B)),
        modifier    = modifier,
    )
}
