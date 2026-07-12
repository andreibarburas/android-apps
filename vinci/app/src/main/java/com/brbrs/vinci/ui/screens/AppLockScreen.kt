package com.brbrs.vinci.ui.screens

import com.brbrs.vinci.R
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.vinci.biometric.BiometricResult
import com.brbrs.vinci.ui.theme.*
import com.brbrs.vinci.ui.viewmodels.AppLockViewModel

@Composable
fun AppLockScreen(
    onUnlocked: () -> Unit,
    onNoLock: () -> Unit,
    onLoggedInNoLock: () -> Unit,
    viewModel: AppLockViewModel = hiltViewModel(),
) {
    val context  = LocalContext.current
    val activity = context as FragmentActivity
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        when {
            uiState.isLoading       -> Unit
            !uiState.isLoggedIn     -> onNoLock()
            !uiState.appLockEnabled -> onLoggedInNoLock()
            uiState.unlocked        -> onUnlocked()
        }
    }

    fun triggerBiometric() {
        viewModel.biometricHelper.authenticate(
            activity = activity,
            subtitle = "Unlock Vinci",
        ) { result ->
            if (result is BiometricResult.Success) viewModel.onUnlocked()
        }
    }

    LaunchedEffect(uiState.appLockEnabled) {
        if (uiState.appLockEnabled && !uiState.unlocked) triggerBiometric()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.background),
                    radius = 1200f,
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .glassCard(cornerRadius = 32.dp, bgAlpha = 0.06f, borderAlpha = 0.12f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Lock, null, tint = SlateText, modifier = Modifier.size(48.dp))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.vinci_logo),
                    contentDescription = "Vinci",
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier.height(48.dp),
                )
                Text(
                    "Touch the sensor to unlock",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(CyanPrimary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = { triggerBiometric() }) {
                    Icon(
                        Icons.Filled.Fingerprint,
                        contentDescription = "Unlock",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }

            Text(
                "Touch to unlock",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            )
        }
    }
}
