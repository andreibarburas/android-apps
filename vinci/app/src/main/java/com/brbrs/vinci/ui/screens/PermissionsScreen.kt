package com.brbrs.vinci.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.vinci.ui.theme.*
import com.brbrs.vinci.ui.viewmodels.PermissionItem
import com.brbrs.vinci.ui.viewmodels.PermissionsViewModel

@Composable
fun PermissionsScreen(
    onAllGranted: () -> Unit,
    viewModel: PermissionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context  = LocalContext.current
    val isDark   = LocalIsDark.current

    // Batch launcher — requests all not-yet-granted permissions at once
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refresh()
    }

    LaunchedEffect(uiState.allEssentialGranted) {
        if (uiState.allEssentialGranted) onAllGranted()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDark) Brush.verticalGradient(listOf(NavyDeep, NavyMid))
                else Brush.verticalGradient(listOf(LightBg, LightSurface2))
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))

            // Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .glassCard(cornerRadius = 24.dp, bgAlpha = 0.08f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.AdminPanelSettings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "A few permissions needed",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Vinci needs access to your contacts and call log to work. Your data stays on your device and your Nextcloud — never anywhere else.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            // Permission cards
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                uiState.permissions.forEach { item ->
                    PermissionCard(item = item, isDark = isDark)
                }
            }

            Spacer(Modifier.height(32.dp))

            // Grant button
            val pending = uiState.permissions.filter { !it.isGranted }.map { it.permission }
            if (pending.isNotEmpty()) {
                Button(
                    onClick = { launcher.launch(pending.toTypedArray()) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("Grant permissions", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(Modifier.height(12.dp))

                // If a permission was permanently denied, point to settings
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                        )
                    }
                ) {
                    Text(
                        "Permissions not showing? Open app settings ->",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Button(
                    onClick = onAllGranted,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Outlined.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("All set — continue", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun PermissionCard(item: PermissionItem, isDark: Boolean) {
    val cardTint = if (isDark) Color.White else Color.Black
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (item.isGranted) Modifier.glassCardPrimary(cornerRadius = 16.dp)
                else Modifier.glassCard(cornerRadius = 16.dp, tint = cardTint)
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Status icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (item.isGranted) CyanPrimary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = permissionIcon(item.permission),
                    contentDescription = null,
                    tint = if (item.isGranted) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!item.isEssential) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "Optional",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    item.rationale,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(10.dp))

            Icon(
                imageVector = if (item.isGranted) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (item.isGranted) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private fun permissionIcon(permission: String): ImageVector = when {
    permission.contains("CONTACTS")         -> Icons.Outlined.Contacts
    permission.contains("CALL_LOG")         -> Icons.Outlined.Call
    permission.contains("PHONE_STATE")      -> Icons.Outlined.PhoneAndroid
    permission.contains("NOTIFICATIONS")    -> Icons.Outlined.Notifications
    else                                    -> Icons.Outlined.Lock
}
