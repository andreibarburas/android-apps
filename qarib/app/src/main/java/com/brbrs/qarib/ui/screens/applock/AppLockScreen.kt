package com.brbrs.qarib.ui.screens.applock

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.qarib.R
import com.brbrs.qarib.biometric.BiometricResult
import com.brbrs.qarib.ui.theme.glassCard

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
            subtitle = "Unlock Qarib",
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
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.qarib_logo),
                    contentDescription = stringResource(R.string.app_name),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier.height(40.dp),
                )
                Text(
                    text = stringResource(R.string.applock_touch_to_unlock_hint),
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
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = { triggerBiometric() }) {
                    Icon(
                        Icons.Filled.Fingerprint,
                        contentDescription = stringResource(R.string.applock_unlock_action),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }

            Text(
                text = stringResource(R.string.applock_touch_to_unlock_action),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            )
        }
    }
}
