package com.brbrs.runa.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.runa.R
import com.brbrs.runa.auth.AuthRepository
import com.brbrs.runa.auth.StorageMode
import androidx.core.content.FileProvider
import com.brbrs.runa.data.repository.ExportRepository
import com.brbrs.runa.data.repository.ExportResult
import com.brbrs.runa.data.repository.SyncRepository
import com.brbrs.runa.data.repository.SyncState
import com.brbrs.runa.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class SettingsUiState(
    val isLoggedIn: Boolean     = false,
    val serverUrl: String       = "",
    val username: String        = "",
    val runaFolder: String      = "Runa",
    val appLockEnabled: Boolean = false,
    val textSize: String        = "default",
    val useCustomFont: Boolean  = false,
    val lastSyncedLabel: String = "Never synced",
    val syncState: SyncState    = SyncState.Idle,
    val isExporting: Boolean    = false,
    val exportError: String?    = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val displayPrefs: DisplayPreferencesRepository,
    private val syncRepository: SyncRepository,
    private val exportRepository: ExportRepository,
) : ViewModel() {

    private val _syncState    = MutableStateFlow<SyncState>(SyncState.Idle)
    private val _isExporting  = MutableStateFlow(false)
    private val _exportError  = MutableStateFlow<String?>(null)

    // Base state from the 5-flow combine (max supported by combine directly)
    private val _baseState: StateFlow<SettingsUiState> = combine(
        authRepository.storageMode,
        authRepository.appLockEnabled,
        displayPrefs.preferences,
        syncRepository.lastSyncedLabel,
        _syncState,
    ) { mode, lock, display, syncLabel, syncState ->
        val connected = mode as? StorageMode.Connected
        SettingsUiState(
            isLoggedIn      = connected != null,
            serverUrl       = connected?.session?.serverUrl ?: "",
            username        = connected?.session?.username  ?: "",
            runaFolder      = connected?.session?.runaFolder ?: "Runa",
            appLockEnabled  = lock,
            textSize        = display.textSize,
            useCustomFont   = display.useCustomFont,
            lastSyncedLabel = syncLabel,
            syncState       = syncState,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    val uiState: StateFlow<SettingsUiState> = combine(
        _baseState,
        _isExporting,
        _exportError,
    ) { base, isExporting, exportError ->
        base.copy(isExporting = isExporting, exportError = exportError)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    private val _signedOut = MutableStateFlow(false)
    val signedOut: StateFlow<Boolean> = _signedOut.asStateFlow()

    fun setAppLock(enabled: Boolean) {
        viewModelScope.launch { authRepository.setAppLockEnabled(enabled) }
    }

    fun setTextSize(size: String) {
        viewModelScope.launch { displayPrefs.setTextSize(size) }
    }

    fun setUseCustomFont(enabled: Boolean) {
        viewModelScope.launch { displayPrefs.setUseCustomFont(enabled) }
    }

    fun updateRunaFolder(folder: String) {
        viewModelScope.launch { authRepository.updateRunaFolder(folder) }
    }

    fun syncNow() {
        if (_syncState.value is SyncState.Syncing) return
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            _syncState.value = syncRepository.sync()
        }
    }

    fun resyncAll() {
        if (_syncState.value is SyncState.Syncing) return
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            _syncState.value = syncRepository.resyncAll()
        }
    }

    private val _exportedFile = MutableStateFlow<java.io.File?>(null)
    val exportedFile: StateFlow<java.io.File?> = _exportedFile.asStateFlow()

    fun exportAll() {
        if (_isExporting.value) return
        viewModelScope.launch {
            _isExporting.value = true
            _exportError.value = null
            when (val result = exportRepository.exportAll()) {
                is ExportResult.Success -> {
                    _exportedFile.value = result.file
                    _isExporting.value = false
                }
                is ExportResult.Error -> {
                    _isExporting.value = false
                    _exportError.value = result.message
                }
            }
        }
    }

    fun onExportFileConsumed() { _exportedFile.value = null }

    fun signOut() {
        viewModelScope.launch {
            authRepository.setLocalMode()
            _signedOut.value = true
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    onConnectNextcloud: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState   by viewModel.uiState.collectAsState()
    val signedOut by viewModel.signedOut.collectAsState()
    val isDark    = LocalIsDark.current
    val context   = LocalContext.current

    var showFolderDialog by remember { mutableStateOf(false) }
    var folderInput      by remember(uiState.runaFolder) { mutableStateOf(uiState.runaFolder) }

    LaunchedEffect(signedOut) { if (signedOut) onSignedOut() }

    val exportedFile by viewModel.exportedFile.collectAsState()
    LaunchedEffect(exportedFile) {
        val file = exportedFile ?: return@LaunchedEffect
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Runa journal"))
        viewModel.onExportFileConsumed()
    }

    Box(modifier = Modifier.fillMaxSize().runaBackground(isDark)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            }

            Spacer(Modifier.height(24.dp))

            // ── Account ───────────────────────────────────────────────────────
            SectionLabel(stringResource(R.string.settings_section_account))
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                if (uiState.isLoggedIn) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(stringResource(R.string.settings_connected_to), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(uiState.serverUrl, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(uiState.username,  style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Icon(Icons.Outlined.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showFolderDialog = true }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(stringResource(R.string.settings_runa_folder_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(uiState.runaFolder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        TextButton(onClick = viewModel::signOut, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_sign_out), color = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_storage_local_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.settings_storage_local_subtitle), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    TextButton(onClick = onConnectNextcloud, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                        Icon(Icons.Outlined.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_connect_nextcloud), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Security ──────────────────────────────────────────────────────
            SectionLabel(stringResource(R.string.settings_section_security))
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.settings_app_lock_title), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.settings_app_lock_subtitle), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = uiState.appLockEnabled,
                        onCheckedChange = viewModel::setAppLock,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Display ───────────────────────────────────────────────────────
            SectionLabel(stringResource(R.string.settings_section_display))
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                Column {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(stringResource(R.string.settings_text_size_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("small" to "Small", "default" to "Default", "large" to "Large", "extra_large" to "Extra Large").forEach { (value, label) ->
                                FilterChip(
                                    selected = uiState.textSize == value,
                                    onClick  = { viewModel.setTextSize(value) },
                                    label    = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f),
                                    shape    = RoundedCornerShape(8.dp),
                                    colors   = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        selectedLabelColor     = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_custom_font_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.settings_custom_font_subtitle), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = uiState.useCustomFont,
                            onCheckedChange = viewModel::setUseCustomFont,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }

            // ── Data (only when connected to Nextcloud) ───────────────────────
            if (uiState.isLoggedIn) {
                Spacer(Modifier.height(20.dp))
                SectionLabel(stringResource(R.string.settings_section_data))
                Spacer(Modifier.height(8.dp))
                SettingsCard(isDark) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = when (uiState.syncState) {
                                    is SyncState.Syncing -> stringResource(R.string.settings_syncing)
                                    is SyncState.Error   -> stringResource(R.string.settings_sync_error)
                                    else                 -> uiState.lastSyncedLabel
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = when (uiState.syncState) {
                                    is SyncState.Error -> MaterialTheme.colorScheme.error
                                    else               -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                            if (uiState.syncState is SyncState.Syncing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                            } else {
                                TextButton(onClick = viewModel::syncNow) {
                                    Text(stringResource(R.string.settings_sync_now), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Re-sync all entries", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text("Re-uploads everything with photos and new folder structure", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (uiState.syncState is SyncState.Syncing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                            } else {
                                TextButton(onClick = viewModel::resyncAll) {
                                    Text("Re-sync", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !uiState.isExporting) { viewModel.exportAll() }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Export journal", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    if (uiState.exportError != null) uiState.exportError!!
                                    else "Download all entries and photos as a ZIP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.exportError != null) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (uiState.isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Support ───────────────────────────────────────────────────────
            SectionLabel(stringResource(R.string.settings_section_support))
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                Column {
                    SupportRow(Icons.Outlined.Favorite, stringResource(R.string.settings_buy_me_a_coffee), stringResource(R.string.settings_buy_me_a_coffee_subtitle)) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://bunq.me/barburasdonations")))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SupportRow(Icons.Outlined.Language, stringResource(R.string.settings_more_by_barburas), stringResource(R.string.settings_more_by_barburas_subtitle)) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://barburas.com")))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SupportRow(Icons.Outlined.Shield, stringResource(R.string.settings_privacy_policy), stringResource(R.string.settings_privacy_policy_subtitle)) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://barburas.com/privacy-policy")))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SupportRow(Icons.Outlined.Code, stringResource(R.string.settings_view_on_github), stringResource(R.string.settings_view_on_github_subtitle)) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/andreibarburas")))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    RedditRow(
                        title    = "Join r/BarburasLab",
                        subtitle = "Discuss Runa and my other apps on Reddit",
                        onClick  = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.reddit.com/r/BarburasLab/"))) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.settings_footer),
                style     = MaterialTheme.typography.labelSmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier  = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    // ── Folder dialog ─────────────────────────────────────────────────────────
    if (showFolderDialog) {
        AlertDialog(
            onDismissRequest = { showFolderDialog = false; folderInput = uiState.runaFolder },
            title = { Text(stringResource(R.string.settings_runa_folder_dialog_title)) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_runa_folder_dialog_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value         = folderInput,
                        onValueChange = { folderInput = it },
                        singleLine    = true,
                        shape         = RoundedCornerShape(12.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            cursorColor        = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.updateRunaFolder(folderInput); showFolderDialog = false }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDialog = false; folderInput = uiState.runaFolder }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun SettingsCard(isDark: Boolean, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().runaCard(isDark, cornerRadius = 16.dp)) { content() }
}

@Composable
private fun RedditRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(
            painter            = painterResource(id = R.drawable.si_reddit),
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.primary,
            modifier           = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title,    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SupportRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title,    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}
