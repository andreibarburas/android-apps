package com.brbrs.blik.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.brbrs.blik.auth.AuthCredentials
import com.brbrs.blik.network.WebDavClient
import com.brbrs.blik.ui.theme.*
import kotlinx.coroutines.launch

/**
 * A full-screen dialog that lets the user browse Nextcloud folders via WebDAV
 * and select one as the upload destination.
 *
 * @param creds       Nextcloud credentials (server, user, password)
 * @param webDavClient injected WebDavClient
 * @param initialPath  folder path to start at, e.g. "/Screenshots"
 * @param onSelected   called with the selected absolute path
 * @param onDismiss    called when the user cancels
 */
@Composable
fun NextcloudFolderPickerDialog(
    creds: AuthCredentials,
    webDavClient: WebDavClient,
    initialPath: String = "/",
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val isDark   = LocalIsDark.current
    val cardTint = if (isDark) Color.White else Color.Black

    // Navigation stack — list of (path, displayName) pairs
    var currentPath by remember { mutableStateOf(normalize(initialPath)) }
    var folders     by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading   by remember { mutableStateOf(false) }
    var error       by remember { mutableStateOf<String?>(null) }
    val scope       = rememberCoroutineScope()

    fun loadFolder(path: String) {
        scope.launch {
            isLoading = true
            error     = null
            try {
                val names = webDavClient.listFolders(creds, path)
                folders = names.sorted()
            } catch (e: Exception) {
                error = e.message ?: "Failed to load folders"
            } finally {
                isLoading = false
            }
        }
    }

    // Load root on first composition
    LaunchedEffect(currentPath) { loadFolder(currentPath) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape  = RoundedCornerShape(20.dp),
            color  = if (isDark) NavyMid else LightSurface,
            tonalElevation = 4.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Header ────────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Back button — go up one level
                    IconButton(
                        onClick = {
                            val parent = currentPath.trimEnd('/').substringBeforeLast('/')
                            currentPath = if (parent.isEmpty()) "/" else parent
                        },
                        enabled = currentPath != "/",
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack, "Up",
                            tint = if (currentPath != "/") MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Select folder",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            currentPath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // ── Select current folder button ──────────────────────────────
                TextButton(
                    onClick = { onSelected(currentPath) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Outlined.CheckCircle, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Use \"${currentPath.trimEnd('/').substringAfterLast('/').ifEmpty { "Root" }}\"",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // ── Folder list ───────────────────────────────────────────────
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        isLoading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                                color    = MaterialTheme.colorScheme.primary,
                            )
                        }
                        error != null -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(error!!, color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { loadFolder(currentPath) }) {
                                    Text("Retry")
                                }
                            }
                        }
                        folders.isEmpty() -> {
                            Text(
                                "No subfolders here",
                                modifier = Modifier.align(Alignment.Center),
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                style    = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        else -> {
                            LazyColumn(
                                contentPadding = PaddingValues(vertical = 8.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(folders) { folderName ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                currentPath = "${currentPath.trimEnd('/')}/$folderName"
                                            }
                                            .padding(horizontal = 20.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        Icon(
                                            Icons.Outlined.Folder, null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp),
                                        )
                                        Text(
                                            folderName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Icon(
                                            Icons.Outlined.ChevronRight, null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(horizontal = 20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun normalize(path: String): String {
    val p = path.trim()
    return if (p.isEmpty()) "/" else if (p.startsWith("/")) p else "/$p"
}
