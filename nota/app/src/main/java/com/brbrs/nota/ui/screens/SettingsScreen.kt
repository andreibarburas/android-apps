package com.brbrs.nota.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.brbrs.nota.ui.theme.LocalIsDark
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.brbrs.nota.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.nota.ui.theme.*
import com.brbrs.nota.ui.viewmodels.SettingsViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.loggedOut) {
        if (uiState.loggedOut) onLoggedOut()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary)
                }
                Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            }

            Spacer(Modifier.height(24.dp))

            // ── Account ───────────────────────────────────────────────────────
            SettingsSectionLabel(stringResource(R.string.section_account))
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(stringResource(R.string.connected_to), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(uiState.serverUrl, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(uiState.username, style = MaterialTheme.typography.bodyMedium, color = CyanPrimary)
                        }
                        Icon(Icons.Outlined.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    TextButton(
                        onClick = viewModel::logout,
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                    ) {
                        Icon(Icons.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.sign_out), color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Security ──────────────────────────────────────────────────────
            SettingsSectionLabel(stringResource(R.string.section_security))
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                SettingsToggleRow(
                    icon = Icons.Outlined.Fingerprint,
                    title = stringResource(R.string.app_lock),
                    subtitle = stringResource(R.string.app_lock_description),
                    checked = uiState.appLockEnabled,
                    onCheckedChange = viewModel::setAppLock,
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Display ──────────────────────────────────────────────────────
            SettingsSectionLabel("Display")
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.TextFields, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Text size", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("Adjust the size of text throughout the app", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        com.brbrs.nota.ui.theme.TextScale.values().forEach { scale ->
                            val selected = uiState.textScale == scale
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(10.dp),
                                    )
                                    .clickable { viewModel.setTextScale(scale) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    scale.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            SettingsCard {
                SettingsToggleRow(
                    icon = Icons.Outlined.TextFields,
                    title = "DM Serif + Inter Tight",
                    subtitle = "Use custom font pairing for note titles and UI",
                    checked = uiState.customFontEnabled,
                    onCheckedChange = viewModel::setCustomFont,
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Integrations ──────────────────────────────────────────────────
            SettingsSectionLabel(stringResource(R.string.section_integrations))
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                SettingsToggleRow(
                    icon = Icons.Outlined.Alarm,
                    title = stringResource(R.string.tasks_org),
                    subtitle = if (uiState.tasksInstalled)
                        stringResource(R.string.tasks_org_description)
                    else
                        stringResource(R.string.tasks_org_not_installed),
                    checked = uiState.tasksEnabled,
                    enabled = uiState.tasksInstalled,
                    onCheckedChange = viewModel::toggleTasks,
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Sync ──────────────────────────────────────────────────────────
            SettingsSectionLabel(stringResource(R.string.section_sync))
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(stringResource(R.string.manual_sync), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.last_synced, uiState.lastSynced), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = viewModel::sync) {
                        Icon(Icons.Outlined.Sync, "Sync now", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Support ───────────────────────────────────────────────────────
            SettingsSectionLabel(stringResource(R.string.section_support))
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                Column {
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://bunq.me/barburasdonations?description=Donation%20from%20Nota"))
                            )
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Icon(Icons.Outlined.Favorite, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.buy_me_coffee), color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.buy_me_coffee_description),
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://barburas.com"))
                            )
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Icon(Icons.Outlined.Language, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.more_by_author), color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.more_by_author_description),
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/andreibarburas/android-apps"))
                            )
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Icon(Icons.Outlined.Code, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.view_on_github), color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.view_on_github_description),
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                stringResource(R.string.app_footer, "1.3.0"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val isDark = LocalIsDark.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .vinciCard(isDark = isDark, cornerRadius = 16.dp),
    ) { content() }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}
