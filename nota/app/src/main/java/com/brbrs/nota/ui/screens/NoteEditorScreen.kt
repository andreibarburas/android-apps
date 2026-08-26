package com.brbrs.nota.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import com.brbrs.nota.ui.theme.DMSerifDisplayFontFamily
import com.brbrs.nota.ui.theme.InterTightFontFamily
import com.brbrs.nota.ui.theme.LocalCustomFontEnabled
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.nota.biometric.BiometricResult
import androidx.compose.ui.res.stringResource
import com.brbrs.nota.R
import com.brbrs.nota.ui.components.AddToTasksButton
import com.brbrs.nota.ui.components.InlineMarkdownWithImages
import com.brbrs.nota.ui.components.NoteImageStrip
import com.brbrs.nota.ui.theme.*
import com.brbrs.nota.ui.viewmodels.NoteEditorViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Long,
    initialCategory: String = "",
    sharedText: String = "",
    sharedImageUri: String = "",
    onBack: () -> Unit,
    viewModel: NoteEditorViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val uiState by viewModel.uiState.collectAsState()
    val customFont = LocalCustomFontEnabled.current
    val noteTitleFont = if (customFont) DMSerifDisplayFontFamily else FontFamily.Serif
    val noteBodyFont  = if (customFont) InterTightFontFamily     else FontFamily.SansSerif
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Image picker — Photo Picker (no permission needed on Android 13+)
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.uploadAndInsertImage(it) }
    }

    BackHandler {
        if (!uiState.showLockOverlay) viewModel.save()
        onBack()
    }

    LaunchedEffect(noteId) { viewModel.loadNote(noteId, initialCategory, sharedText) }

    LaunchedEffect(sharedImageUri) {
        if (sharedImageUri.isNotBlank()) {
            viewModel.uploadAndInsertImage(android.net.Uri.parse(sharedImageUri))
        }
    }

    // Show snackbar on upload error
    LaunchedEffect(uiState.uploadError) {
        uiState.uploadError?.let { msg ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Upload failed: $msg",
                    duration = SnackbarDuration.Long,
                )
                viewModel.clearUploadError()
            }
        }
    }

    LaunchedEffect(uiState.showLockOverlay) {
        if (uiState.showLockOverlay) {
            viewModel.biometricHelper.authenticate(
                activity = activity,
                subtitle = "Unlock this note",
            ) { result ->
                if (result is BiometricResult.Success) viewModel.unlockNote()
            }
        }
    }

    val isDark = LocalIsDark.current
    val bgGradient = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.background,
        )
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGradient)
                .padding(scaffoldPadding),
        ) {
            // Hero radial glow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(
                        Brush.radialGradient(
                            colors = if (isDark)
                                listOf(Color(0x1A00C853), Color.Transparent)
                            else
                                listOf(Color(0x144CAF78), Color.Transparent),
                            radius = 600f,
                        )
                    ),
            )

            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = {
                    if (!uiState.showLockOverlay) viewModel.save()
                    onBack()
                }) {
                    Icon(Icons.Outlined.ArrowBack, stringResource(R.string.back), tint = MaterialTheme.colorScheme.primary)
                }
                if (!uiState.showLockOverlay) {
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        val tasksEnabled by viewModel.tasksEnabled.collectAsState()
                        AddToTasksButton(
                            noteTitle    = uiState.title,
                            noteContent  = uiState.content,
                            tasksEnabled = tasksEnabled,
                            iconOnly     = true,
                        )
                        IconButton(onClick = viewModel::togglePreview) {
                            Icon(
                                if (uiState.previewMode) Icons.Outlined.Edit else Icons.Outlined.Preview,
                                stringResource(R.string.toggle_preview),
                                tint = if (uiState.previewMode) CyanPrimary else SlateText,
                            )
                        }
                        IconButton(onClick = viewModel::toggleLock) {
                            Icon(
                                if (uiState.isLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                                stringResource(R.string.lock_note),
                                tint = if (uiState.isLocked) CyanPrimary else SlateText,
                            )
                        }
                        IconButton(onClick = { viewModel.deleteNote(); onBack() }) {
                            Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Outlined.MoreVert, stringResource(R.string.more), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ── Editor content ────────────────────────────────────────────────
            if (!uiState.showLockOverlay) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = 56.dp),
                ) {
                    val categories by viewModel.categories.collectAsState()
                    var categoryExpanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = it },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                        ) {
                            Icon(Icons.Outlined.Tag, null, tint = CyanPrimary.copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = uiState.category.ifBlank { stringResource(R.string.category_label) },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (uiState.category.isBlank()) SlateTextDim else CyanPrimary.copy(alpha = 0.7f),
                                modifier = Modifier.weight(1f),
                            )
                            if (categories.isNotEmpty()) {
                                Icon(
                                    if (categoryExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                    null,
                                    tint = SlateTextDim,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        ExposedDropdownMenu(
                            expanded = categoryExpanded && categories.isNotEmpty(),
                            onDismissRequest = { categoryExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.no_category), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) },
                                onClick = { viewModel.onCategoryChanged(""); categoryExpanded = false },
                            )
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyMedium) },
                                    onClick = { viewModel.onCategoryChanged(cat); categoryExpanded = false },
                                    trailingIcon = if (uiState.category == cat) {
                                        { Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                )
                            }
                            HorizontalDivider(color = GlassBorder)
                            DropdownMenuItem(
                                text = {
                                    BasicTextField(
                                        value = if (uiState.category !in categories) uiState.category else "",
                                        onValueChange = viewModel::onCategoryChanged,
                                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontFamily = noteBodyFont),
                                        cursorBrush = SolidColor(CyanPrimary),
                                        decorationBox = { inner ->
                                            if (uiState.category.isBlank() || uiState.category in categories) {
                                                Text(stringResource(R.string.new_category), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                            }
                                            inner()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                },
                                onClick = {},
                            )
                        }
                    }

                    HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

                    // ── Title ─────────────────────────────────────────────────
                    BasicTextField(
                        value = uiState.title,
                        onValueChange = viewModel::onTitleChanged,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 28.sp,
                            fontFamily = noteTitleFont,
                            lineHeight = 36.sp,
                        ),
                        cursorBrush = SolidColor(CyanPrimary),
                        decorationBox = { inner ->
                            Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                                if (uiState.title.isEmpty()) {
                                    Text(stringResource(R.string.title_placeholder), style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 28.sp, fontFamily = noteTitleFont))
                                }
                                inner()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(8.dp))

                    // ── Upload indicator ──────────────────────────────────────
                    if (uiState.isUploading) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                stringResource(R.string.uploading_image),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // ── Body ──────────────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    ) {
                        val imageLoader by viewModel.imageLoader.collectAsState()

                        if (uiState.previewMode) {
                            // Preview: render text and images inline at their markdown positions
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                InlineMarkdownWithImages(
                                    markdown = uiState.content,
                                    imageLoader = imageLoader,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        } else {
                            // Edit mode: raw markdown + images strip below
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                BasicTextField(
                                    value = uiState.contentField,
                                    onValueChange = viewModel::onContentChanged,
                                    textStyle = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        lineHeight = 24.sp,
                                        fontFamily = noteBodyFont,
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    decorationBox = { inner ->
                                        Box {
                                            if (uiState.content.isEmpty()) {
                                                Text(stringResource(R.string.start_writing), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            inner()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                NoteImageStrip(
                                    markdown = uiState.content,
                                    imageLoader = imageLoader,
                                    maxImages = 10,
                                    cropImages = false,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }
                        }
                    }

                    // ── Markdown toolbar ──────────────────────────────────────
                    if (!uiState.previewMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                                .imePadding()
                                .navigationBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Image picker button
                            IconButton(
                                onClick = {
                                    imagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.AddPhotoAlternate,
                                    contentDescription = stringResource(R.string.insert_image),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            // Divider between image button and text formatters
                            Box(
                                modifier = Modifier
                                    .height(20.dp)
                                    .width(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant),
                            )

                            listOf(
                                "B"   to "**bold**",
                                "I"   to "_italic_",
                                "#"   to "# ",
                                "—"   to "---\n",
                                "[ ]" to "- [ ] ",
                            ).forEach { (label, insert) ->
                                ToolbarBtn(label = label, onClick = { viewModel.insertMarkdown(insert) })
                            }
                        }
                    }
                }
            }

            // ── Lock overlay ──────────────────────────────────────────────────
            if (uiState.showLockOverlay) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .glassCard(cornerRadius = 32.dp, bgAlpha = 0.06f, borderAlpha = 0.12f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.note_locked), style = MaterialTheme.typography.headlineMedium.copy(fontFamily = noteTitleFont), color = MaterialTheme.colorScheme.onBackground)
                            Text(
                                stringResource(R.string.note_locked_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(CyanPrimary.copy(alpha = 0.10f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconButton(onClick = {
                                viewModel.biometricHelper.authenticate(
                                    activity = activity,
                                    subtitle = "Unlock this note",
                                ) { result ->
                                    if (result is BiometricResult.Success) viewModel.unlockNote()
                                }
                            }) {
                                Icon(Icons.Filled.Fingerprint, stringResource(R.string.unlock_note), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                            }
                        }

                        Text(stringResource(R.string.touch_to_unlock), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))

                        TextButton(onClick = onBack) {
                            Text(stringResource(R.string.go_back), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarBtn(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
