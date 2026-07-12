package com.brbrs.blik.ui.screens.nextcloud

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.brbrs.blik.ui.theme.*
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NextcloudScreen(
    vm: NextcloudViewModel = hiltViewModel(),
    imageLoader: ImageLoader,
) {
    val state  by vm.uiState.collectAsState()
    val isDark = state.isDark
    val iconTint    = MaterialTheme.colorScheme.onSurfaceVariant
    val titleColor  = MaterialTheme.colorScheme.onBackground
    val subColor    = MaterialTheme.colorScheme.onSurfaceVariant
    val cardTint    = if (isDark) Color.White else Color.Black
    val isSelecting = state.isSelecting
    val selectedCount = state.selectedPaths.size
    val allSelected = selectedCount == state.files.size && state.files.isNotEmpty()

    val bgBrush = if (isDark)
        Brush.verticalGradient(listOf(NavyDeep, NavyMid, NavyDeep))
    else
        Brush.verticalGradient(listOf(LightBg, LightSurface, LightSurface2))

    // Pull-to-refresh
    val pullState = rememberPullToRefreshState()
    if (pullState.isRefreshing) { LaunchedEffect(Unit) { vm.refresh() } }
    LaunchedEffect(state.isLoading) { if (!state.isLoading) pullState.endRefresh() }

    // File info bottom sheet
    var selectedFile by remember { mutableStateOf<RemoteFile?>(null) }
    val sheetState   = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (selectedFile != null) {
        ModalBottomSheet(
            onDismissRequest  = { selectedFile = null },
            sheetState        = sheetState,
            containerColor    = if (isDark) NavySurface else LightSurface,
            dragHandle        = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) },
        ) {
            val file = selectedFile!!
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Preview thumbnail if available locally
                if (file.localPath != null) {
                    AsyncImage(
                        model              = android.net.Uri.parse(file.localPath),
                        contentDescription = file.fileName,
                        contentScale       = ContentScale.Fit,
                        modifier           = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(16.dp)),
                    )
                } else if (file.webDavUrl.isNotBlank()) {
                    val context = LocalContext.current
                    val request = remember(file.webDavUrl, file.authHeader) {
                        ImageRequest.Builder(context)
                            .data(file.webDavUrl)
                            .addHeader("Authorization", file.authHeader)
                            .crossfade(true)
                            .build()
                    }
                    AsyncImage(
                        model              = request,
                        imageLoader        = imageLoader,
                        contentDescription = file.fileName,
                        contentScale       = ContentScale.Fit,
                        modifier           = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(16.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isDark) NavyMid else LightSurface2),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.CloudQueue, null,
                                tint = subColor, modifier = Modifier.size(32.dp))
                            Text("No local copy", style = MaterialTheme.typography.labelSmall,
                                color = subColor)
                        }
                    }
                }

                // File name
                Text(file.fileName,
                    style = MaterialTheme.typography.titleLarge,
                    color = titleColor)

                // Info rows
                InfoSheet("Remote path", file.remotePath)
                InfoSheet("Location", if (isDark) file.remoteFolder else file.remoteFolder)
                InfoSheet("Local copy",
                    if (file.isAvailableLocally) "Available on this device"
                    else "Remote only — not on this phone")

                // Delete from Nextcloud button
                OutlinedButton(
                    onClick = {
                        selectedFile = null
                        vm.deleteFileDirectly(file)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    ),
                ) {
                    Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete from Nextcloud")
                }
            }
        }
    }

    // Delete multi-select dialog
    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = vm::dismissDeleteDialog,
            title = { Text("Delete $selectedCount file${if (selectedCount > 1) "s" else ""} from Nextcloud?") },
            text  = { Text("Files still on your phone will be marked as pending upload again.") },
            confirmButton = {
                TextButton(onClick = vm::deleteSelectedFromNextcloud) {
                    Text("Delete from Nextcloud", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissDeleteDialog) { Text("Cancel") }
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .nestedScroll(pullState.nestedScrollConnection),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────────
            if (isSelecting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = vm::clearSelection) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Cancel",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    Text("$selectedCount selected",
                        style = MaterialTheme.typography.titleLarge,
                        color = titleColor, modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (allSelected) vm.clearSelection() else vm.selectAll() }) {
                        Icon(
                            if (allSelected) Icons.Outlined.Deselect else Icons.Outlined.SelectAll,
                            null, tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = vm::showDeleteDialog, enabled = selectedCount > 0) {
                        Icon(Icons.Outlined.Delete, "Delete",
                            tint = if (selectedCount > 0) MaterialTheme.colorScheme.error
                                   else subColor)
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        androidx.compose.foundation.Image(
                            painter          = androidx.compose.ui.res.painterResource(id = com.brbrs.blik.R.drawable.blik_logo),
                            contentDescription = "Blik",
                            contentScale     = androidx.compose.ui.layout.ContentScale.Fit,
                            colorFilter      = if (!isDark)
                                androidx.compose.ui.graphics.ColorFilter.tint(androidx.compose.ui.graphics.Color(0xFF1E293B))
                            else null,
                            modifier         = Modifier
                                .width(100.dp)
                                .heightIn(max = 44.dp),
                        )
                        Text(
                            if (state.isLoading) "Loading…"
                            else if (state.files.isEmpty()) "Pull down to load"
                            else "${state.files.size} files · tap for info · long-press to select",
                            style = MaterialTheme.typography.bodyMedium, color = subColor,
                        )
                    }
                    IconButton(onClick = vm::refresh) {
                        if (state.isLoading)
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else
                            Icon(Icons.Outlined.Sync, "Refresh", tint = iconTint)
                    }
                }
            }

            // ── Error banner ──────────────────────────────────────────────────
            state.error?.let { err ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(err, modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = vm::dismissError, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Outlined.Close, null, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // ── Not logged in ─────────────────────────────────────────────────
            if (!state.isLoggedIn) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Outlined.CloudOff, null,
                            tint = subColor, modifier = Modifier.size(48.dp))
                        Text("Not connected to Nextcloud",
                            color = subColor, style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center)
                    }
                }
                return@Column
            }

            // ── Empty state ───────────────────────────────────────────────────
            if (!state.isLoading && state.files.isEmpty() && state.error == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Outlined.CloudQueue, null,
                            tint = subColor, modifier = Modifier.size(48.dp))
                        Text("Pull down to load your Nextcloud screenshots",
                            color = subColor, style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center)
                    }
                }
                return@Column
            }

            // ── File grid ─────────────────────────────────────────────────────
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.files, key = { it.remotePath }) { file ->
                    val isSelected = file.remotePath in state.selectedPaths
                    RemoteFileThumb(
                        file        = file,
                        cardTint    = cardTint,
                        isDark      = isDark,
                        isSelecting = isSelecting,
                        isSelected  = isSelected,
                        imageLoader = imageLoader,
                        onClick     = {
                            if (isSelecting) vm.toggleSelection(file.remotePath)
                            else selectedFile = file
                        },
                        onLongClick = { vm.toggleSelection(file.remotePath) },
                    )
                }
            }
        }

        // Pull-to-refresh indicator
        PullToRefreshContainer(
            state         = pullState,
            modifier      = Modifier.align(Alignment.TopCenter),
            contentColor  = MaterialTheme.colorScheme.primary,
            containerColor = if (isDark) NavySurface else LightSurface,
        )

        // Delete FAB
        if (isSelecting && selectedCount > 0) {
            ExtendedFloatingActionButton(
                onClick        = vm::showDeleteDialog,
                modifier       = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 24.dp, bottom = 24.dp),
                containerColor = MaterialTheme.colorScheme.error,
                contentColor   = Color.White,
                shape          = RoundedCornerShape(18.dp),
                icon           = { Icon(Icons.Outlined.Delete, null) },
                text           = { Text("Delete $selectedCount") },
            )
        }
    }
}

@Composable
private fun InfoSheet(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f))
        Text(value, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(0.65f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RemoteFileThumb(
    file: RemoteFile,
    cardTint: Color,
    isDark: Boolean,
    isSelecting: Boolean,
    isSelected: Boolean,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.border(2.dp, primary, RoundedCornerShape(14.dp))
                else Modifier
            )
            .blikCard(isDark = isDark, cornerRadius = 14.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (file.localPath != null) {
                    // Local copy available — load from content URI
                    AsyncImage(
                        model              = android.net.Uri.parse(file.localPath),
                        contentDescription = file.fileName,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize(),
                    )
                } else if (file.webDavUrl.isNotBlank()) {
                    // Remote only — load via authenticated WebDAV request
                    val request = remember(file.webDavUrl, file.authHeader) {
                        ImageRequest.Builder(context)
                            .data(file.webDavUrl)
                            .addHeader("Authorization", file.authHeader)
                            .crossfade(true)
                            .build()
                    }
                    SubcomposeAsyncImage(
                        model              = request,
                        imageLoader        = imageLoader,
                        contentDescription = file.fileName,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize(),
                        error              = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(if (isDark) NavySurface else LightSurface2),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.Cloud, null,
                                    tint = primary.copy(alpha = 0.35f),
                                    modifier = Modifier.size(36.dp))
                            }
                        },
                    )
                } else {
                    // Fallback placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (isDark) NavySurface else LightSurface2),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Cloud, null,
                            tint = primary.copy(alpha = 0.35f),
                            modifier = Modifier.size(36.dp))
                    }
                }

                // Selection checkbox
                if (isSelecting) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) primary else Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(Icons.Outlined.Check, null,
                                tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Local / remote badge
                if (!isSelecting) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (file.isAvailableLocally) Color(0xFF4ADE80).copy(alpha = 0.9f)
                                else Color.Black.copy(alpha = 0.55f)
                            )
                            .padding(horizontal = 5.dp, vertical = 3.dp),
                    ) {
                        Text(
                            if (file.isAvailableLocally) "📱" else "☁️",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                // Category chip
                val category = file.remoteFolder.trimEnd('/').substringAfterLast('/')
                    .takeIf { it.isNotBlank() && file.remoteFolder.count { c -> c == '/' } > 1 }
                if (category != null && !isSelecting) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(NavyDeep.copy(alpha = 0.75f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(category, style = MaterialTheme.typography.labelSmall,
                            color = CyanPrimary)
                    }
                }
            }

            // Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    file.fileName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
