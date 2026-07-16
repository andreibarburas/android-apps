package com.brbrs.blik.ui.screens.detail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.brbrs.blik.data.local.UploadStatus
import com.brbrs.blik.tasks.TasksOrgHelper
import com.brbrs.blik.ui.theme.*
import com.brbrs.blik.util.ShareHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
    initialPath: String,
    allPaths: List<String> = listOf(initialPath),
    onBack: () -> Unit,
    vm: DetailViewModel = hiltViewModel(),
) {
    val paths        = remember(allPaths) { allPaths.ifEmpty { listOf(initialPath) } }
    val initialIndex = remember(initialPath, paths) { paths.indexOf(initialPath).coerceAtLeast(0) }
    val pagerState   = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = initialIndex,
        pageCount   = { paths.size },
    )

    // Keep the VM loaded for the current page
    val currentPath = paths.getOrNull(pagerState.currentPage) ?: initialPath
    LaunchedEffect(currentPath) { vm.load(currentPath) }

    androidx.compose.foundation.pager.HorizontalPager(
        state    = pagerState,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        val pagePath = paths.getOrNull(page) ?: return@HorizontalPager
        // Only the current/adjacent pages need a real VM — others just show a placeholder
        if (page == pagerState.currentPage ||
            page == pagerState.currentPage - 1 ||
            page == pagerState.currentPage + 1) {
            DetailPageContent(
                localPath = pagePath,
                isCurrentPage = page == pagerState.currentPage,
                onBack = onBack,
                vm = vm,
            )
        }
    }
}

@Composable
private fun DetailPageContent(
    localPath: String,
    isCurrentPage: Boolean,
    onBack: () -> Unit,
    vm: DetailViewModel,
) {
    val state  by vm.uiState.collectAsState()
    val entity = state.entity
    val context = LocalContext.current
    val isDark  = LocalIsDark.current
    val cardTint = if (isDark) Color.White else Color.Black

    LaunchedEffect(localPath, isCurrentPage) {
        if (isCurrentPage) vm.load(localPath)
    }

    // Android 11+ system delete confirmation dialog
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            vm.onDeleteConfirmed(onBack)
        } else {
            vm.onDeleteCancelled()
        }
    }

    // Launch system dialog whenever a pending IntentSender appears
    val pendingSender = state.pendingDeleteSender
    LaunchedEffect(pendingSender) {
        pendingSender?.let {
            deleteLauncher.launch(IntentSenderRequest.Builder(it).build())
        }
    }

    val bgBrush = if (isDark)
        Brush.verticalGradient(listOf(NavyDeep, NavyMid, NavyDeep))
    else
        Brush.verticalGradient(listOf(LightBg, LightSurface, LightSurface2))

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showFullscreen   by remember { mutableStateOf(false) }
    var showShareDialog  by remember { mutableStateOf(false) }
    val isUploaded = entity?.uploadStatus == com.brbrs.blik.data.local.UploadStatus.UPLOADED

    // ── Share dialog ──────────────────────────────────────────────────────────
    if (showShareDialog && entity != null && !entity.isLocalFileMissing) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text("Share screenshot") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            showShareDialog = false
                            ShareHelper.shareImage(context, Uri.parse(entity.localPath), entity.fileName)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share original")
                    }
                    Button(
                        onClick = {
                            showShareDialog = false
                            vm.showCropScreen()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.Crop, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Crop, then share")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showShareDialog = false }) { Text("Cancel") }
            },
        )
    }

    // ── Crop screen overlay ───────────────────────────────────────────────────
    if (state.showCropScreen && entity != null) {
        CropScreen(
            localPath = entity.localPath,
            onDismiss = vm::hideCropScreen,
            onCropped = { bitmap ->
                vm.hideCropScreen()
                ShareHelper.shareBitmap(context, bitmap, entity.fileName)
            },
        )
        return   // don't render detail UI beneath crop screen
    }

    // ── Fullscreen image viewer ───────────────────────────────────────────────
    if (showFullscreen && entity != null && !entity.isLocalFileMissing) {
        FullscreenImageViewer(
            localPath = entity.localPath,
            fileName  = entity.fileName,
            onDismiss = { showFullscreen = false },
        )
    }

    // ── Smart delete dialog ───────────────────────────────────────────────────
    if (showDeleteDialog && entity != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete screenshot") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isUploaded) {
                        Text("This screenshot is on Nextcloud. Choose what to delete:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        // Option 1: phone only
                        OutlinedButton(
                            onClick = { showDeleteDialog = false; vm.deleteLocalOnly(onBack) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Outlined.PhoneAndroid, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Delete from phone only")
                        }
                        // Option 2: Nextcloud only
                        OutlinedButton(
                            onClick = { showDeleteDialog = false; vm.deleteNextcloudOnly() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Outlined.Cloud, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Delete from Nextcloud only")
                        }
                        // Option 3: everywhere
                        Button(
                            onClick = { showDeleteDialog = false; vm.deleteEverywhere(onBack) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Icon(Icons.Outlined.DeleteForever, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Delete from both")
                        }
                    } else {
                        Text("Delete this screenshot from your phone? This cannot be undone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                if (!isUploaded) {
                    TextButton(onClick = { showDeleteDialog = false; vm.delete(onBack) }) {
                        Text("Delete from phone", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                }
            },
            dismissButton = {
                if (!isUploaded) {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                }
            },
        )
    }

    // ── Note dialog ───────────────────────────────────────────────────────────
    if (state.showNoteDialog) {
        var noteText by remember { mutableStateOf(entity?.note ?: "") }
        AlertDialog(
            onDismissRequest = vm::hideNoteDialog,
            title = { Text("Note") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    placeholder = { Text("Add a note…") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor        = MaterialTheme.colorScheme.primary,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.saveNote(noteText) }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = vm::hideNoteDialog) { Text("Cancel") }
            },
        )
    }

    // ── Tag dialog ────────────────────────────────────────────────────────────
    if (state.showTagDialog) {
        var tagText by remember { mutableStateOf(entity?.tags ?: "") }
        AlertDialog(
            onDismissRequest = vm::hideTagDialog,
            title = { Text("Tags") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Separate tags with commas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = tagText,
                        onValueChange = { tagText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("travel, amsterdam, 2025") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            cursorColor        = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.saveTags(tagText) }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = vm::hideTagDialog) { Text("Cancel") }
            },
        )
    }

    // ── Task dialog ───────────────────────────────────────────────────────────
    if (state.showTaskDialog) {
        var taskTitle by remember { mutableStateOf(entity?.fileName ?: "") }
        var taskNotes by remember { mutableStateOf(entity?.note ?: "") }
        AlertDialog(
            onDismissRequest = vm::hideTaskDialog,
            title = { Text("Add Task") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = taskTitle, onValueChange = { taskTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Task title") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            cursorColor        = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    OutlinedTextField(
                        value = taskNotes, onValueChange = { taskNotes = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                        label = { Text("Notes") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            cursorColor        = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    TasksOrgHelper.createTask(context, taskTitle, taskNotes)
                    vm.hideTaskDialog()
                }) { Text("Open in Tasks.org") }
            },
            dismissButton = {
                TextButton(onClick = vm::hideTaskDialog) { Text("Cancel") }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(bgBrush)) {
        if (entity == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                // ── Top bar ───────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(entity.fileName, style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1, modifier = Modifier.weight(1f))
                    // Overflow menu
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, "More",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Delete from Blik", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Delete, null,
                                        tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = { showMenu = false; showDeleteDialog = true },
                            )
                        }
                    }
                }

                // ── Screenshot preview ────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 640.dp)
                        .padding(horizontal = 16.dp)
                        .glassCard(cornerRadius = 16.dp, tint = cardTint)
                        .then(
                            if (!entity.isLocalFileMissing)
                                Modifier.clickable { showFullscreen = true }
                            else Modifier
                        ),
                ) {
                    if (entity.isLocalFileMissing) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Outlined.CloudDone, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp))
                                Text("File deleted from phone",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Available on Nextcloud",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else {
                        AsyncImage(
                            model              = android.net.Uri.parse(entity.localPath),
                            contentDescription = entity.fileName,
                            contentScale       = ContentScale.Fit,
                            modifier           = Modifier.fillMaxWidth(),
                        )
                        // Tap hint
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.45f))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        ) {
                            Text("Tap to expand",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Upload status row ─────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val (dotColor, statusText) = when (entity.uploadStatus) {
                            UploadStatus.UPLOADED  -> Pair(SuccessGreen, "Uploaded to ${entity.remoteFolder}")
                            UploadStatus.UPLOADING -> Pair(CyanPrimary, "Uploading…")
                            UploadStatus.ERROR     -> Pair(ErrorRed, "Error: ${entity.uploadErrorMsg}")
                            UploadStatus.PENDING   -> Pair(WarnYellow, "Pending upload")
                        }
                        Box(modifier = Modifier.size(8.dp).background(dotColor, shape = androidx.compose.foundation.shape.CircleShape))
                        Text(statusText, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── Action buttons ────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Star
                    ActionButton(
                        label   = if (entity.isStarred) "Starred" else "Star",
                        icon    = if (entity.isStarred) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                        primary = entity.isStarred,
                        modifier = Modifier.weight(1f),
                        onClick = vm::toggleStar,
                    )
                    // Upload / Uploaded
                    val isUploaded = entity.uploadStatus == UploadStatus.UPLOADED
                    ActionButton(
                        label   = when {
                            state.isUploading            -> "…"
                            entity.isLocalFileMissing    -> "No file"
                            isUploaded                   -> "Uploaded"
                            else                         -> "Upload"
                        },
                        icon    = when {
                            entity.isLocalFileMissing -> Icons.Outlined.CloudOff
                            isUploaded                -> Icons.Outlined.CloudDone
                            else                      -> Icons.Outlined.CloudUpload
                        },
                        primary = isUploaded && !entity.isLocalFileMissing,
                        enabled = !state.isUploading
                            && !entity.isLocalFileMissing
                            && entity.uploadStatus != UploadStatus.UPLOADING,
                        modifier = Modifier.weight(1f),
                        onClick = vm::upload,
                    )
                    // Note
                    ActionButton(
                        label   = "Note",
                        icon    = Icons.Outlined.EditNote,
                        primary = entity.note.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        onClick = vm::showNoteDialog,
                    )
                    // Tags
                    ActionButton(
                        label   = "Tags",
                        icon    = Icons.Outlined.Tag,
                        primary = entity.tags.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        onClick = vm::showTagDialog,
                    )
                    // Task
                    if (state.tasksEnabled) {
                        ActionButton(
                            label   = "Task",
                            icon    = Icons.Outlined.CheckCircle,
                            modifier = Modifier.weight(1f),
                            onClick = vm::showTaskDialog,
                        )
                    }
                    // AI
                    ActionButton(
                        label   = if (state.isAiLoading) "…" else "AI",
                        icon    = Icons.Outlined.AutoAwesome,
                        enabled = !state.isAiLoading,
                        modifier = Modifier.weight(1f),
                        onClick = vm::runAi,
                    )
                    // Share
                    if (!entity.isLocalFileMissing) {
                        ActionButton(
                            label   = "Share",
                            icon    = Icons.Outlined.Share,
                            modifier = Modifier.weight(1f),
                            onClick = { showShareDialog = true },
                        )
                    }
                }

                // ── AI error ──────────────────────────────────────────────────
                state.aiError?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .background(ErrorRed.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(err, Modifier.weight(1f), color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = vm::dismissAiError, Modifier.size(20.dp)) {
                            Icon(Icons.Outlined.Close, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── AI Description ────────────────────────────────────────────
                if (entity.aiDescription.isNotBlank()) {
                    SectionLabel("AI Description")
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .glassCard(cornerRadius = 14.dp, tint = cardTint),
                    ) {
                        Column(modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text("✦ ${state.aiModel}", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            Text(entity.aiDescription, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground)
                            if (entity.category.isNotBlank()) {
                                Text("Category: ${entity.category}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ── Note ──────────────────────────────────────────────────────
                if (entity.note.isNotBlank()) {
                    SectionLabel("Note")
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .glassCard(cornerRadius = 14.dp, tint = cardTint),
                    ) {
                        Text(entity.note, modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground)
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ── Tags ──────────────────────────────────────────────────────
                if (entity.tags.isNotBlank()) {
                    SectionLabel("Tags")
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        entity.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(8).forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text("#$tag", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ── Info ──────────────────────────────────────────────────────
                SectionLabel("Info")
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .glassCard(cornerRadius = 14.dp, tint = cardTint),
                ) {
                    Column(modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoRow("File", entity.fileName)
                        InfoRow("Captured", SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                            .format(Date(entity.capturedAt)))
                        InfoRow("Size", formatBytes(entity.fileSizeBytes))
                        InfoRow("Hash", entity.fileHash.take(12) + "…")
                        if (entity.remotePath.isNotBlank())
                            InfoRow("Remote", entity.remotePath)
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    primary: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val primary2 = MaterialTheme.colorScheme.primary
    val isDark   = LocalIsDark.current
    val tint     = if (isDark) Color.White else Color.Black
    Box(
        modifier = modifier
            .glassCard(
                cornerRadius = 12.dp,
                tint = if (primary) primary2 else tint,
                bgAlpha = if (primary) 0.15f else 0.07f,
                borderAlpha = if (primary) 0.30f else 0.10f,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(icon, null,
                tint = if (primary) primary2
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f),
                modifier = Modifier.size(18.dp))
            Text(label,
                style = MaterialTheme.typography.labelSmall,
                color = if (primary) primary2
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f))
        }
    }
}

@Composable
private fun InfoRow(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = FontFamily.Monospace, fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onBackground)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    return "%.1f MB".format(kb / 1024.0)
}

@Composable
private fun FullscreenImageViewer(
    localPath: String,
    fileName: String,
    onDismiss: () -> Unit,
) {
    var scale       by remember { mutableStateOf(1f) }
    var offset      by remember { mutableStateOf(Offset.Zero) }
    var showChrome  by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress      = true,
            dismissOnClickOutside   = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale  = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale == 1f) Offset.Zero
                                 else offset + pan
                    }
                }
                // Single tap toggles the back button visibility
                .clickable { showChrome = !showChrome },
        ) {
            // Image — fills the screen, zoomable
            AsyncImage(
                model              = android.net.Uri.parse(localPath),
                contentDescription = fileName,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX       = scale,
                        scaleY       = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            )

            // Back button — fades in/out on tap
            AnimatedVisibility(
                visible = showChrome,
                enter   = fadeIn(),
                exit    = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Close fullscreen",
                        tint     = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            // Filename label at bottom
            AnimatedVisibility(
                visible  = showChrome,
                enter    = fadeIn(),
                exit     = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(fileName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
    }
}
