package com.brbrs.blik.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.brbrs.blik.ui.theme.*

/**
 * Wraps [content] with a runtime permission gate for reading images.
 * On Android 13+ requests READ_MEDIA_IMAGES; on older versions READ_EXTERNAL_STORAGE.
 * Shows a rationale screen if permission is denied.
 */
@Composable
fun MediaPermissionGate(content: @Composable () -> Unit) {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_IMAGES
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

    var granted by remember { mutableStateOf(false) }
    var denied  by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        granted = isGranted
        denied  = !isGranted
    }

    LaunchedEffect(Unit) {
        launcher.launch(permission)
    }

    when {
        granted -> content()
        denied  -> PermissionDeniedScreen(onRetry = {
            denied = false
            launcher.launch(permission)
        })
        // Still waiting for the system dialog — show nothing (dialog is on top)
        else -> {}
    }
}

@Composable
private fun PermissionDeniedScreen(onRetry: () -> Unit) {
    val isDark = LocalIsDark.current
    val bgBrush = if (isDark)
        Brush.verticalGradient(listOf(NavyDeep, NavyMid, NavyDeep))
    else
        Brush.verticalGradient(listOf(LightBg, LightSurface, LightSurface2))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                Modifier.run {
                    // inline background brush
                    this
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(40.dp),
        ) {
            Icon(
                Icons.Outlined.PhotoLibrary, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Text(
                "Photo access needed",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                "Blik needs access to your photos to scan and upload screenshots. Please grant permission to continue.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onRetry,
                shape  = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor   = if (isDark) NavyDeep else androidx.compose.ui.graphics.Color.White,
                ),
            ) {
                Text("Grant permission")
            }
        }
    }
}
