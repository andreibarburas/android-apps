package com.brbrs.nota.ui.screens

import com.brbrs.nota.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.nota.biometric.BiometricHelper
import com.brbrs.nota.biometric.BiometricResult
import com.brbrs.nota.ui.theme.*
import com.brbrs.nota.ui.viewmodels.AppLockViewModel
import javax.inject.Inject

@Composable
fun AppLockScreen(
    onUnlocked: () -> Unit,
    onNoLock: () -> Unit,
    onLoggedInNoLock: () -> Unit,
    viewModel: AppLockViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        when {
            uiState.isLoading -> Unit
            !uiState.isLoggedIn -> onNoLock()
            !uiState.appLockEnabled -> onLoggedInNoLock()
            uiState.unlocked -> onUnlocked()
        }
    }

    fun triggerBiometric() {
        viewModel.biometricHelper.authenticate(
            activity = activity,
            subtitle = "Unlock Nóta",
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
            // Glass lock icon
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .glassCard(cornerRadius = 32.dp, bgAlpha = 0.06f, borderAlpha = 0.12f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = SlateText,
                    modifier = Modifier.size(48.dp),
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.nota_logo),
                        contentDescription = "Nóta",
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                        modifier = androidx.compose.ui.Modifier.height(48.dp),
                    )
                Text(
                    "Touch the sensor to unlock",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Fingerprint button
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(CyanPrimary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = { triggerBiometric() }) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
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
