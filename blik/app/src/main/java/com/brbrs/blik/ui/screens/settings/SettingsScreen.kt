package com.brbrs.blik.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.blik.auth.AuthCredentials
import com.brbrs.blik.network.WebDavClient
import com.brbrs.blik.ui.components.NextcloudFolderPickerDialog
import com.brbrs.blik.ui.theme.*

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    webDavClient: WebDavClient,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state   by vm.uiState.collectAsState()
    val context = LocalContext.current
    val isDark  = LocalIsDark.current

    val bgBrush = if (isDark)
        Brush.verticalGradient(listOf(NavyDeep, NavyMid, NavyDeep))
    else
        Brush.verticalGradient(listOf(LightBg, LightSurface, LightSurface2))

    LaunchedEffect(state.loggedOut) { if (state.loggedOut) onLoggedOut() }

    var showClaudeKey        by remember { mutableStateOf(false) }
    var showOpenAiKey        by remember { mutableStateOf(false) }
    var showNextcloudPicker  by remember { mutableStateOf(false) }

    // Local folder picker — Android system folder picker via SAF
    val localFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Convert content URI to real path string for display & file access
            // e.g. content://com.android.externalstorage.documents/tree/primary:Pictures/Screenshots
            val path = uri.path
                ?.removePrefix("/tree/primary:")
                ?.removePrefix("/tree/")
                ?.let { "/$it" }
                ?: uri.toString()
            // Persist permission so we can read across sessions
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            vm.setLocalFolder(uri.toString())   // store the URI string
            vm.setLocalFolderDisplay(path)       // store human-readable path for display
        }
    }

    // Nextcloud folder picker dialog
    if (showNextcloudPicker) {
        val creds = state.credentials
        if (creds != null) {
            NextcloudFolderPickerDialog(
                creds        = creds,
                webDavClient = webDavClient,
                initialPath  = state.remoteFolder.ifBlank { "/" },
                onSelected   = { path ->
                    vm.setRemoteFolder(path)
                    showNextcloudPicker = false
                },
                onDismiss = { showNextcloudPicker = false },
            )
        }
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
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back",
                        tint = MaterialTheme.colorScheme.primary)
                }
                Text("Settings", style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground)
            }

            Spacer(Modifier.height(20.dp))

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
                            Text("Connected to", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(state.serverUrl ?: "—", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground)
                            Text(state.username ?: "—", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        Icon(Icons.Outlined.Cloud, null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    TextButton(onClick = vm::logout,
                        modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = ErrorRed,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sign out", color = ErrorRed)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── FOLDERS ───────────────────────────────────────────────────────
            SectionLabel("Folders")
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                Column {
                    // Local folder — tap to open system folder picker
                    FolderPickerRow(
                        icon        = Icons.Outlined.PhoneAndroid,
                        label       = "Screenshots folder (on phone)",
                        value       = state.localFolderDisplay.ifBlank { state.localFolder }.ifBlank { "Tap to select…" },
                        placeholder = true,
                        onClick     = { localFolderLauncher.launch(null) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    // Nextcloud folder — tap to open WebDAV browser
                    FolderPickerRow(
                        icon        = Icons.Outlined.CloudUpload,
                        label       = "Destination folder (on Nextcloud)",
                        value       = state.remoteFolder.ifBlank { "Tap to select…" },
                        placeholder = state.remoteFolder.isBlank(),
                        enabled     = state.credentials != null,
                        onClick     = { showNextcloudPicker = true },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── AUTO UPLOAD ───────────────────────────────────────────────────
            SectionLabel("Auto Upload")
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                Column {
                    ToggleRow(Icons.Outlined.Sync, "Auto upload",
                        "Upload new screenshots automatically",
                        state.autoUpload, onCheckedChange = {
                            vm.setAutoUpload(it)
                            if (it) vm.scheduleAutoUpload()
                        })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    ToggleRow(Icons.Outlined.Wifi, "Wi-Fi only",
                        "Skip upload on mobile data", state.wifiOnly, onCheckedChange = vm::setWifiOnly)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    ToggleRow(Icons.Outlined.BatteryChargingFull, "Charging only",
                        "Upload when plugged in", state.chargingOnly, onCheckedChange = vm::setChargingOnly)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Warning, null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("On conflict", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground)
                                Text("When file already exists on Nextcloud",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { expanded = true }) {
                                Text(state.onConflict, color = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                listOf("ASK", "SKIP", "OVERWRITE").forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt) },
                                        onClick = { vm.setOnConflict(opt); expanded = false },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── AI ────────────────────────────────────────────────────────────
            SectionLabel("AI")
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                Column {
                    SettingsTextField(
                        icon        = Icons.Outlined.AutoAwesome,
                        label       = "Claude API key",
                        placeholder = "sk-ant-…",
                        value       = state.claudeApiKey,
                        onValueChange = vm::setClaudeApiKey,
                        isPassword  = true,
                        showPassword = showClaudeKey,
                        onToggleShow = { showClaudeKey = !showClaudeKey },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SettingsTextField(
                        icon        = Icons.Outlined.Psychology,
                        label       = "OpenAI API key",
                        placeholder = "sk-…",
                        value       = state.openAiApiKey,
                        onValueChange = vm::setOpenAiApiKey,
                        isPassword  = true,
                        showPassword = showOpenAiKey,
                        onToggleShow = { showOpenAiKey = !showOpenAiKey },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.SmartToy, null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Default model", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground)
                        }
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { expanded = true }) {
                                Text(state.aiModel, color = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                listOf("CLAUDE", "OPENAI").forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt) },
                                        onClick = { vm.setAiModel(opt); expanded = false },
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    ToggleRow(Icons.Outlined.Category, "Auto-categorize",
                        "AI assigns category folder on upload", state.autoCategorize, onCheckedChange = vm::setAutoCategorize)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    ToggleRow(Icons.Outlined.Description, "Auto-describe",
                        "AI generates description on upload", state.autoAiDesc, onCheckedChange = vm::setAutoAiDesc)
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── SECURITY ──────────────────────────────────────────────────────
            SectionLabel("Security")
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                ToggleRow(
                    icon     = Icons.Outlined.Fingerprint,
                    title    = "App lock",
                    subtitle = "Require biometrics when opening Blik",
                    checked  = state.biometricEnabled,
                    enabled  = state.biometricAvailable,
                    onCheckedChange = vm::toggleBiometric,
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── APPEARANCE ────────────────────────────────────────────────────
            SectionLabel("Appearance")
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.TextFields, null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Text size", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground)
                            Text("Adjust the size of text throughout the app",
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
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(10.dp),
                                    )
                                    .clickable { vm.setTextScale(scale) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    scale.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                ToggleRow(
                    icon     = Icons.Outlined.TextFields,
                    title    = "DM Serif & Inter Tight",
                    subtitle = "Use suite fonts: DM Serif Display for headings, Inter Tight for UI",
                    checked  = state.useInterTight,
                    onCheckedChange = vm::toggleInterTight,
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── INTEGRATIONS ──────────────────────────────────────────────────
            SectionLabel("Integrations")
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                ToggleRow(
                    icon     = Icons.Outlined.Alarm,
                    title    = "Tasks.org",
                    subtitle = if (state.tasksInstalled) "Add screenshots as tasks in Tasks.org"
                               else "Tasks.org is not installed",
                    checked  = state.tasksEnabled,
                    enabled  = state.tasksInstalled,
                    onCheckedChange = vm::toggleTasks,
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── SUPPORT ───────────────────────────────────────────────────────
            SectionLabel("Support")
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                Column {
                    TextButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://bunq.me/barburasdonations?description=Donation%20from%20Blik")))
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Icon(Icons.Outlined.Favorite, null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Buy me a coffee", color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodyMedium)
                            Text("If Blik saves you time, a small tip means a lot",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    TextButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://barburas.com")))
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Icon(Icons.Outlined.Language, null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("More by andrei BARBURAS",
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodyMedium)
                            Text("See what else I'm building at barburas.com",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    TextButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/andreibarburas/android-apps")))
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Icon(Icons.Outlined.Code, null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("View on GitHub",
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodyMedium)
                            Text("Report bugs, request features, view source",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Text("Blik 1.1.1 · by andrei BARBURAS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp))
        }
    }
}

// ── Private composables ────────────────────────────────────────────────────────

@Composable
private fun FolderPickerRow(
    icon: ImageVector,
    label: String,
    value: String,
    placeholder: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, null,
                tint = if (enabled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (placeholder) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                )
            }
        }
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(Icons.Outlined.FolderOpen, "Pick folder",
                tint = if (enabled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp))
        }
    }
}

@Composable private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable private fun SettingsCard(isDark: Boolean, content: @Composable () -> Unit) {
    val tint = if (isDark) Color.White else Color.Black
    Box(modifier = Modifier.fillMaxWidth().glassCard(cornerRadius = 16.dp, tint = tint)) { content() }
}

@Composable private fun ToggleRow(
    icon: ImageVector, title: String, subtitle: String,
    checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(subtitle, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor   = MaterialTheme.colorScheme.primary,
                checkedTrackColor   = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ))
    }
}

@Composable private fun SettingsTextField(
    icon: ImageVector, label: String, placeholder: String,
    value: String, onValueChange: (String) -> Unit,
    isPassword: Boolean = false, showPassword: Boolean = false,
    onToggleShow: (() -> Unit)? = null,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
            singleLine = true,
            visualTransformation = if (isPassword && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = if (isPassword) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
            trailingIcon = if (isPassword && onToggleShow != null) {
                {
                    IconButton(onClick = onToggleShow) {
                        Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor    = Color.Transparent,
                focusedTextColor        = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor      = MaterialTheme.colorScheme.onBackground,
                cursorColor             = MaterialTheme.colorScheme.primary,
                focusedContainerColor   = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
        )
    }
}
