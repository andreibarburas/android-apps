package com.brbrs.blik.ui.screens.applock

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.brbrs.blik.R
import com.brbrs.blik.biometric.BiometricHelper
import com.brbrs.blik.ui.theme.*

@Composable
fun AppLockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val isDark  = LocalIsDark.current

    val bgBrush = if (isDark)
        Brush.verticalGradient(listOf(NavyDeep, NavyMid, NavyDeep))
    else
        Brush.verticalGradient(listOf(LightBg, LightSurface, LightSurface2))

    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        BiometricHelper.prompt(
            activity  = context as FragmentActivity,
            title     = "Unlock Blik",
            subtitle  = "Confirm your identity to continue",
            onSuccess = { onUnlocked() },
            onFail    = { errorMsg = it },
        )
    }

    Box(
        modifier = Modifier.fillMaxSize().background(bgBrush),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            // Lock icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .glassCard(
                        cornerRadius = 22.dp,
                        tint = if (isDark) androidx.compose.ui.graphics.Color.White
                               else androidx.compose.ui.graphics.Color.Black
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Lock, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp))
            }

            // Handwritten logo
            Image(
                painter          = painterResource(id = R.drawable.blik_logo),
                contentDescription = "Blik",
                contentScale     = ContentScale.Fit,
                colorFilter      = if (!isDark)
                    ColorFilter.tint(androidx.compose.ui.graphics.Color(0xFF1E293B))
                else null,
                modifier         = Modifier
                    .fillMaxWidth(0.55f)
                    .heightIn(max = 72.dp),
            )

            errorMsg?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }

            Button(
                onClick = {
                    errorMsg = null
                    BiometricHelper.prompt(
                        activity  = context as FragmentActivity,
                        onSuccess = { onUnlocked() },
                        onFail    = { errorMsg = it },
                    )
                },
                shape  = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
            ) {
                Text("Unlock",
                    color = if (isDark) NavyDeep else androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}
