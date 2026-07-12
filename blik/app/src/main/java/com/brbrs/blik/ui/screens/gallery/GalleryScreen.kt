package com.brbrs.blik.ui.screens.gallery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.brbrs.blik.data.local.ScreenshotEntity
import com.brbrs.blik.data.local.UploadStatus
import com.brbrs.blik.ui.components.MediaPermissionGate
import com.brbrs.blik.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onOpenDetail: (String) -> Unit,
    onSettings: () -> Unit,
    vm: GalleryViewModel = hiltViewModel(),
) {
    MediaPermissionGate {
        GalleryContent(onOpenDetail = onOpenDetail, onSettings = onSettings, vm = vm)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun GalleryContent(
    onOpenDetail: (String) -> Unit,
    onSettings: () -> Unit,
    vm: GalleryViewModel,
) {
    val state      by vm.uiState.collectAsState()
    val isDark     = state.isDark
    val isSelecting = state.isSelecting
    val cardTint   = if (isDark) Color.White else Color.Black
    val iconTint   = MaterialTheme.colorScheme.onSurfaceVariant
    val titleColor = MaterialTheme.colorScheme.onBackground
    val subColor   = MaterialTheme.colorScheme.onSurfaceVariant

    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar for upload results
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
            vm.dismissSnackbar()
        }
    }

    // Android 11+ system delete confirmation dialog
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) vm.onDeleteConfirmed()
        else vm.onDeleteCancelled()
    }
    val pendingDeleteSender = state.pendingDeleteSender
    LaunchedEffect(pendingDeleteSender) {
        pendingDeleteSender?.let {
            deleteLauncher.launch(IntentSenderRequest.Builder(it).build())
        }
    }

    // Three-stop vertical background gradient
    val bgBrush = if (isDark)
        Brush.verticalGradient(listOf(NavyDeep, NavyMid, NavyDeep))
    else
        Brush.verticalGradient(listOf(LightBg, LightSurface, LightSurface2))

    // Radial glow at top — amber in dark, soft amber in light
    val radialGlow = remember(isDark) {
        object : ShaderBrush() {
            override fun createShader(size: androidx.compose.ui.geometry.Size) =
                RadialGradientShader(
                    center = androidx.compose.ui.geometry.Offset(size.width / 2f, 0f),
                    radius = size.width * 0.75f,
                    colors = if (isDark)
                        listOf(Color(0x22FF8F00), Color.Transparent)
                    else
                        listOf(Color(0x15F57C00), Color.Transparent),
                )
        }
    }

    val pendingCount   = state.screenshots.count {
        it.uploadStatus == UploadStatus.PENDING || it.uploadStatus == UploadStatus.ERROR
    }
    val selectedCount  = state.selectedPaths.size
    val allSelected    = selectedCount == state.screenshots.size && state.screenshots.isNotEmpty()

    // Delete confirmation dialog
    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete $selectedCount screenshot${if (selectedCount > 1) "s" else ""}?") },
            text  = { Text("The selected files will be deleted from your phone. If any are uploaded to Nextcloud, they will remain there.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; vm.deleteSelected() }) {
                    Text("Delete from phone", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(bgBrush)) {
        // Radial glow overlay at top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(radialGlow),
        )

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar — normal or selection mode ────────────────────────────
            if (isSelecting) {
                // Selection action bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = vm::clearSelection) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Cancel selection",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        "$selectedCount selected",
                        style = MaterialTheme.typography.titleLarge,
                        color = titleColor,
                        modifier = Modifier.weight(1f),
                    )
                    // Select all / deselect all toggle
                    IconButton(onClick = { if (allSelected) vm.clearSelection() else vm.selectAll() }) {
                        Icon(
                            if (allSelected) Icons.Outlined.Deselect else Icons.Outlined.SelectAll,
                            if (allSelected) "Deselect all" else "Select all",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    // Blur / unblur selected
                    IconButton(onClick = vm::toggleBlurSelected) {
                        Icon(Icons.Outlined.BlurOn, "Blur / unblur selected",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    // Star / unstar selected
                    IconButton(onClick = vm::toggleStarSelected) {
                        Icon(Icons.Outlined.Star, "Star / unstar selected",
                            tint = Color(0xFFFBBF24))
                    }
                    // Upload selected
                    IconButton(
                        onClick = vm::uploadSelected,
                        enabled = selectedCount > 0,
                    ) {
                        Icon(Icons.Outlined.CloudUpload, "Upload selected",
                            tint = if (selectedCount > 0) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Delete selected
                    IconButton(
                        onClick  = { showDeleteDialog = true },
                        enabled  = selectedCount > 0,
                    ) {
                        Icon(Icons.Outlined.Delete, "Delete selected",
                            tint = if (selectedCount > 0) MaterialTheme.colorScheme.error
                                   else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                // Normal top bar
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
                            "${state.screenshots.size} screenshots" +
                                if (pendingCount > 0) " · $pendingCount pending" else " · all synced",
                            style = MaterialTheme.typography.bodyMedium,
                            color = subColor,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = vm::scanFolder) {
                            if (state.isScanning)
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else
                                Icon(Icons.Outlined.Refresh, "Scan folder", tint = iconTint)
                        }
                        IconButton(onClick = vm::uploadAllPending) {
                            Icon(Icons.Outlined.CloudUpload, "Upload pending", tint = iconTint)
                        }
                        IconButton(onClick = vm::toggleTheme) {
                            Icon(
                                if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                null, tint = iconTint,
                            )
                        }
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Outlined.Settings, "Settings", tint = iconTint)
                        }
                    }
                }
            }

            // ── Search (hidden during selection) ──────────────────────────────
            if (!isSelecting) {
                SearchBar(
                    modifier       = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    query          = state.searchQuery,
                    onQueryChange  = vm::onSearchChanged,
                    onSearch       = {},
                    active         = false,
                    onActiveChange = {},
                    placeholder    = { Text("Search screenshots…", color = subColor) },
                    leadingIcon    = { Icon(Icons.Outlined.Search, null, tint = iconTint) },
                    trailingIcon   = if (state.searchQuery.isNotEmpty()) {
                        { IconButton(onClick = { vm.onSearchChanged("") }) {
                            Icon(Icons.Outlined.Close, null, tint = iconTint) } }
                    } else null,
                    colors = SearchBarDefaults.colors(
                        containerColor = if (isDark) GlassWhite else LightSurface,
                        inputFieldColors = TextFieldDefaults.colors(
                            focusedTextColor   = titleColor,
                            unfocusedTextColor = titleColor,
                            cursorColor        = MaterialTheme.colorScheme.primary,
                        ),
                    ),
                    tonalElevation = 0.dp,
                ) {}
                Spacer(Modifier.height(12.dp))
            }

            // ── Filter chips (hidden during selection) ────────────────────────
            if (!isSelecting) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip("All", state.selectedFilter == GalleryFilter.ALL && state.selectedCategory == null, isDark) {
                            vm.onFilterSelected(GalleryFilter.ALL); vm.onCategorySelected(null)
                        }
                    }
                    item { FilterChip("Pending", state.selectedFilter == GalleryFilter.PENDING, isDark) { vm.onFilterSelected(GalleryFilter.PENDING) } }
                    item { FilterChip("Uploaded", state.selectedFilter == GalleryFilter.UPLOADED, isDark) { vm.onFilterSelected(GalleryFilter.UPLOADED) } }
                    item { FilterChip("Notes", state.selectedFilter == GalleryFilter.NOTES, isDark) { vm.onFilterSelected(GalleryFilter.NOTES) } }
                item { FilterChip("⭐ Starred", state.selectedFilter == GalleryFilter.STARRED, isDark) { vm.onFilterSelected(GalleryFilter.STARRED) } }
                    items(state.categories) { cat ->
                        FilterChip(cat.replaceFirstChar { it.uppercase() }, state.selectedCategory == cat, isDark) {
                            vm.onCategorySelected(if (state.selectedCategory == cat) null else cat)
                            vm.onFilterSelected(GalleryFilter.ALL)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Error banner ──────────────────────────────────────────────────
            state.scanError?.let { err ->
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
                    IconButton(onClick = vm::dismissScanError, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Outlined.Close, null, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // ── Grid ──────────────────────────────────────────────────────────
            if (state.screenshots.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.PhotoLibrary, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp))
                        Text(
                            if (state.searchQuery.isNotBlank()) "No results"
                            else "Tap ↺ to scan your screenshot folder",
                            color = subColor, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.screenshots, key = { it.localPath }) { ss ->
                        val isSelected = ss.localPath in state.selectedPaths
                        ScreenshotThumb(
                            entity      = ss,
                            cardTint    = cardTint,
                            isSelecting = isSelecting,
                            isSelected  = isSelected,
                            onClick     = {
                                if (isSelecting) vm.toggleSelection(ss.localPath)
                                else onOpenDetail(ss.localPath)
                            },
                            onLongClick  = { vm.toggleSelection(ss.localPath) },
                            onUpload     = { vm.uploadSingle(ss) },
                            onToggleBlur = {
                                if (isSelecting) vm.toggleBlurSelected()
                                else vm.toggleBlur(ss)
                            },
                            onToggleStar = { vm.toggleStar(ss) },
                        )
                    }
                }
            }
        }

        // ── Snackbar ──────────────────────────────────────────────────────────
        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 80.dp),  // above FAB
            snackbar  = { data ->
                val isError = data.visuals.message.startsWith("✗")
                Snackbar(
                    snackbarData    = data,
                    containerColor  = if (isError) MaterialTheme.colorScheme.error
                                      else if (isDark) NavySurface else LightSurface,
                    contentColor    = if (isError) Color.White
                                      else MaterialTheme.colorScheme.onBackground,
                    shape           = RoundedCornerShape(12.dp),
                )
            }
        )

        // ── FAB ───────────────────────────────────────────────────────────────
        if (isSelecting && selectedCount > 0) {
            // Two stacked FABs in selection mode
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                // Delete FAB
                ExtendedFloatingActionButton(
                    onClick        = { showDeleteDialog = true },
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor   = Color.White,
                    shape          = RoundedCornerShape(18.dp),
                    icon           = { Icon(Icons.Outlined.Delete, null) },
                    text           = { Text("Delete $selectedCount") },
                )
                // Upload FAB
                ExtendedFloatingActionButton(
                    onClick        = vm::uploadSelected,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor   = if (isDark) NavyDeep else Color.White,
                    shape          = RoundedCornerShape(18.dp),
                    icon           = { Icon(Icons.Outlined.CloudUpload, null) },
                    text           = { Text("Upload $selectedCount") },
                )
            }
        } else if (!isSelecting && pendingCount > 0) {
            // Upload all pending FAB in normal mode
            ExtendedFloatingActionButton(
                onClick = vm::uploadAllPending,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 24.dp, bottom = 24.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor   = if (isDark) NavyDeep else Color.White,
                shape = RoundedCornerShape(18.dp),
                icon  = { Icon(Icons.Outlined.CloudUpload, null) },
                text  = { Text("Upload $pendingCount") },
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, isDark: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .blikChip(isDark = isDark, selected = selected)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScreenshotThumb(
    entity: ScreenshotEntity,
    cardTint: Color,
    isSelecting: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onUpload: () -> Unit,
    onToggleBlur: () -> Unit,
    onToggleStar: () -> Unit,
) {
    val primary  = MaterialTheme.colorScheme.primary
    val isDark   = LocalIsDark.current
    // Uploaded screenshots get elevated treatment; pending/error get standard card
    val isElevated = entity.uploadStatus == UploadStatus.UPLOADED && !isSelecting
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected)
                    Modifier.border(2.dp, primary, RoundedCornerShape(14.dp))
                else
                    Modifier
            )
            .then(
                if (isElevated)
                    Modifier.blikCardElevated(isDark = isDark, cornerRadius = 14.dp)
                else
                    Modifier.blikCard(isDark = isDark, cornerRadius = 14.dp)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f)) {
                AsyncImage(
                    model              = android.net.Uri.parse(entity.localPath),
                    contentDescription = entity.fileName,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        .then(
                            if (entity.isBlurred)
                                Modifier.blur(20.dp)
                            else
                                Modifier
                        ),
                )
                // Remote-only overlay — shown when local file has been deleted externally
                if (entity.isLocalFileMissing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(NavyDeep.copy(alpha = 0.75f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Outlined.CloudDone, null,
                                tint = CyanPrimary.copy(alpha = 0.8f),
                                modifier = Modifier.size(28.dp),
                            )
                            Text(
                                "On Nextcloud",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanPrimary.copy(alpha = 0.8f),
                            )
                        }
                    }
                }

                // Blur indicator overlay — subtle icon so user knows it's blurred
                if (entity.isBlurred && !isSelecting) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(8.dp),
                    ) {
                        Icon(Icons.Outlined.BlurOn, null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(22.dp))
                    }
                }

                // Star badge on image corner when starred
                if (entity.isStarred && !isSelecting) {
                    Text(
                        "⭐",
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                // Selection checkbox overlay
                if (isSelecting) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) primary
                                else Color.Black.copy(alpha = 0.45f)
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(Icons.Outlined.Check, null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Upload status badge — hidden while selecting
                if (!isSelecting) {
                    val (badgeColor, badgeIcon) = when (entity.uploadStatus) {
                        UploadStatus.UPLOADED  -> Pair(Color(0xFF4ADE80), "✓")
                        UploadStatus.UPLOADING -> Pair(CyanPrimary, "↑")
                        UploadStatus.ERROR     -> Pair(ErrorRed, "!")
                        UploadStatus.PENDING   -> Pair(WarnYellow, "⏳")
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(badgeColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(badgeIcon, style = MaterialTheme.typography.labelSmall, color = Color.Black)
                    }
                }

                // Note / tags indicator
                if (entity.note.isNotBlank() || entity.tags.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(primary.copy(alpha = 0.85f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(
                            when {
                                entity.note.isNotBlank() && entity.tags.isNotBlank() -> "📝 #"
                                entity.note.isNotBlank() -> "📝"
                                else -> "#"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Black,
                        )
                    }
                }

                // Category chip
                if (entity.category.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(NavyDeep.copy(alpha = 0.75f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(entity.category, style = MaterialTheme.typography.labelSmall,
                            color = CyanPrimary)
                    }
                }
            }

            // Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    entity.fileName.take(18),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // Star toggle button
                IconButton(onClick = onToggleStar, modifier = Modifier.size(24.dp)) {
                    Icon(
                        if (entity.isStarred) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                        if (entity.isStarred) "Unstar" else "Star",
                        tint = if (entity.isStarred) Color(0xFFFBBF24)
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp),
                    )
                }
                // Blur toggle button — visible both in normal and selection mode
                IconButton(onClick = onToggleBlur, modifier = Modifier.size(24.dp)) {
                    Icon(
                        if (entity.isBlurred) Icons.Outlined.BlurOff else Icons.Outlined.BlurOn,
                        if (entity.isBlurred) "Unblur" else "Blur",
                        tint = if (entity.isBlurred) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (!isSelecting &&
                    (entity.uploadStatus == UploadStatus.PENDING || entity.uploadStatus == UploadStatus.ERROR)) {
                    IconButton(onClick = onUpload, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Outlined.CloudUpload, "Upload",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
