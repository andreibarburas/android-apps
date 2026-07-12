package com.brbrs.merk.ui.screens.applock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.brbrs.merk.biometric.BiometricHelper
import com.brbrs.merk.ui.components.MerkLogo
import com.brbrs.merk.ui.theme.*

@Composable
fun AppLockScreen(onUnlocked: () -> Unit) {
    val context  = LocalContext.current
    val activity = context as FragmentActivity
    val isDark   = LocalIsDark.current
    var error by remember { mutableStateOf<String?>(null) }

    val bgBrush = if (isDark)
        Brush.radialGradient(listOf(NavySurface, NavyDeep), radius = 1200f)
    else
        Brush.radialGradient(listOf(LightSurface2, LightBg), radius = 1200f)

    fun triggerBiometric() {
        BiometricHelper.prompt(
            activity  = activity,
            title     = "Merk",
            subtitle  = "Unlock Merk",
            onSuccess = onUnlocked,
            onFail    = { msg -> error = msg },
        )
    }

    LaunchedEffect(Unit) { triggerBiometric() }

    Box(
        modifier = Modifier.fillMaxSize().background(bgBrush),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .glassCard(
                        cornerRadius = 32.dp, bgAlpha = 0.06f, borderAlpha = 0.12f,
                        tint = if (isDark) androidx.compose.ui.graphics.Color.White
                               else androidx.compose.ui.graphics.Color.Black,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Lock, null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(48.dp))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MerkLogo(modifier = Modifier.height(48.dp))
                Text(
                    error ?: "Touch the sensor to unlock",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (error != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = { error = null; triggerBiometric() }) {
                    Icon(Icons.Filled.Fingerprint, "Unlock",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp))
                }
            }

            Text("Touch to unlock", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        }
    }
}
