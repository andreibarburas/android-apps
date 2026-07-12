package com.brbrs.merk.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.merk.R
import com.brbrs.merk.ui.theme.*

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state   by vm.uiState.collectAsState()
    val context = LocalContext.current
    val isDark  = LocalIsDark.current

    val bgBrush = if (isDark)
        Brush.verticalGradient(listOf(NavyDeep, NavyMid))
    else
        Brush.verticalGradient(listOf(LightBg, LightSurface2))

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }

    Box(modifier = Modifier.fillMaxSize().background(bgBrush)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "Back",
                        tint = MaterialTheme.colorScheme.primary)
                }
                Text("Settings", style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground)
            }

            Spacer(Modifier.height(24.dp))

            // ── ACCOUNT ───────────────────────────────────────────────────────
            SectionLabel("Account")
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Connected to",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(state.serverUrl ?: "—",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground)
                            Text(state.username ?: "—",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        Icon(Icons.Outlined.Cloud, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    TextButton(
                        onClick  = { vm.logout() },
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                    ) {
                        Icon(Icons.Outlined.Logout, null,
                            tint = ErrorRed, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sign out", color = ErrorRed)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── SECURITY ──────────────────────────────────────────────────────
            SectionLabel("Security")
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                ToggleRow(
                    icon     = Icons.Outlined.Fingerprint,
                    title    = "App lock",
                    subtitle = "Require biometrics when opening Merk",
                    checked  = state.biometricEnabled,
                    enabled  = state.biometricAvailable,
                    onCheckedChange = vm::toggleBiometric,
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── DISPLAY ───────────────────────────────────────────────────────
            SectionLabel("Display")
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.FormatSize, null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Text size",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground)
                            Text("Adjust the size of text throughout Merk",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextScale.entries.forEach { scale ->
                            val selected = state.textScale == scale
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                                else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .clickable { vm.setTextScale(scale) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    scale.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SettingsCard(isDark) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Outlined.FontDownload, null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "DM Serif + Inter Tight",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                "Use custom font pairing instead of system fonts",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked         = state.useCustomFont,
                        onCheckedChange = vm::setUseCustomFont,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("Integrations")
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                ToggleRow(
                    icon     = Icons.Outlined.Alarm,
                    title    = "Tasks.org",
                    subtitle = if (state.tasksInstalled)
                        "Add bookmarks as reminders in Tasks.org"
                    else
                        "Tasks.org is not installed",
                    checked  = state.tasksEnabled,
                    enabled  = state.tasksInstalled,
                    onCheckedChange = vm::toggleTasks,
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── SYNC ──────────────────────────────────────────────────────────
            SectionLabel("Sync")
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Manual sync",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            if (state.isSyncing) "Syncing..." else "Last synced: ${state.lastSynced}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    } else {
                        IconButton(onClick = vm::sync) {
                            Icon(Icons.Outlined.Sync, "Sync now",
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── SUPPORT ───────────────────────────────────────────────────────
            SectionLabel("Support")
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                Column {
                    // 1. Donation
                    SupportRow(
                        icon     = Icons.Outlined.Favorite,
                        title    = "Buy me a coffee",
                        subtitle = "If Merk saves you time, a small tip means a lot",
                        onClick  = {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://bunq.me/barburasdonations?description=Donation%20from%20Merk")))
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    // 2. Website
                    SupportRow(
                        icon     = Icons.Outlined.Language,
                        title    = "More by andrei BARBURAS",
                        subtitle = "See what else I'm building at barburas.com",
                        onClick  = {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://barburas.com")))
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    // 3. GitHub
                    SupportRow(
                        icon     = Icons.Outlined.Code,
                        title    = "View on GitHub",
                        subtitle = "Report issues, request features, or contribute",
                        onClick  = {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/andreibarburas/android-apps")))
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    // 4. Reddit
                    SupportRow(
                        icon     = painterResource(R.drawable.ic_reddit),
                        title    = "Join r/BarburasLab",
                        subtitle = "Discuss Merk and my other apps on Reddit",
                        onClick  = {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://www.reddit.com/r/BarburasLab/")))
                        },
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            Text(
                "Merk 1.1.1 · by andrei BARBURAS",
                style     = MaterialTheme.typography.labelSmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun SettingsCard(isDark: Boolean, content: @Composable () -> Unit) {
    val tint = if (isDark) Color.White else Color.Black
    Box(modifier = Modifier.fillMaxWidth().glassCard(cornerRadius = 16.dp, tint = tint)) {
        content()
    }
}

@Composable
private fun SupportRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Icon(icon, null,
            tint     = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium)
            Text(subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SupportRow(
    icon: Painter,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Icon(icon, null,
            tint     = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium)
            Text(subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, null,
                tint     = if (enabled) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked         = checked,
            enabled         = enabled,
            onCheckedChange = onCheckedChange,
            colors          = SwitchDefaults.colors(
                checkedThumbColor   = MaterialTheme.colorScheme.primary,
                checkedTrackColor   = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}
