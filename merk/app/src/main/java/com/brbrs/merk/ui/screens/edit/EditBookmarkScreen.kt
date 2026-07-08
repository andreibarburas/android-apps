package com.brbrs.merk.ui.screens.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.merk.ui.components.RemindMeButton
import com.brbrs.merk.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBookmarkScreen(
    prefillUrl:   String? = null,
    prefillTitle: String? = null,
    tasksEnabled: Boolean = false,
    onSaved:   () -> Unit,
    onDismiss: () -> Unit,
    vm: EditBookmarkViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsState()
    val isDark = LocalIsDark.current
    val isEdit = state.id > 0
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle back gesture / back button — dismiss like the X button
    BackHandler(enabled = !state.isSaved) { onDismiss() }

    val bgBrush = if (isDark) Brush.verticalGradient(listOf(NavyDeep, NavyMid))
                  else Brush.verticalGradient(listOf(LightBg, LightSurface2))

    LaunchedEffect(prefillUrl) {
        if (prefillUrl != null) vm.prefill(prefillUrl, prefillTitle)
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            // Dismiss keyboard first so snackbar appears above it
            keyboardController?.hide()
            val msg = if (isEdit) "Bookmark updated" else "Bookmark saved"
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
            onSaved()
        }
    }

    // ── New folder dialog ─────────────────────────────────────────────────────
    if (state.showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = vm::hideNewFolderDialog,
            title = { Text("New folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value         = state.newFolderName,
                        onValueChange = vm::onNewFolderNameChanged,
                        label         = { Text("Folder name") },
                        singleLine    = true,
                        isError       = state.folderError != null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { vm.createFolder() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor     = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor   = MaterialTheme.colorScheme.onBackground,
                            cursorColor          = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    state.folderError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = vm::createFolder, enabled = !state.isCreatingFolder) {
                    if (state.isCreatingFolder) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text("Create", color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = vm::hideNewFolderDialog) { Text("Cancel") }
            },
            containerColor = if (isDark) NavyMid else LightSurface,
        )
    }

    // ── Main screen ───────────────────────────────────────────────────────────
    Scaffold(
        modifier       = Modifier.background(bgBrush),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            prefillUrl != null -> "Save to Merk"
                            isEdit             -> "Edit bookmark"
                            else               -> "Add bookmark"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, "Cancel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    TextButton(
                        onClick  = vm::save,
                        enabled  = !state.isLoading && !state.isSaved,
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text("Save", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar  = { data ->
                    Snackbar(
                        snackbarData   = data,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor   = if (isDark) NavyDeep else Color.White,
                        shape          = RoundedCornerShape(12.dp),
                    )
                }
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }

            if (prefillUrl != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Share, null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp))
                    Text("Shared from browser",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }

            BookmarkTextField(state.url, vm::onUrlChanged, "URL *", "https://",
                keyboardType = KeyboardType.Uri)
            BookmarkTextField(state.title, vm::onTitleChanged, "Title", "Leave blank to use URL")
            BookmarkTextField(state.description, vm::onDescriptionChanged,
                "Description", "Optional notes", singleLine = false, minLines = 2)

            // ── Tags ──────────────────────────────────────────────────────────
            Text("Tags", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                state.tags.forEach { tag ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(tag, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                        Icon(Icons.Outlined.Close, "Remove",
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp).clickable { vm.removeTag(tag) })
                    }
                }
                OutlinedTextField(
                    value         = state.tagInput,
                    onValueChange = vm::onTagInputChanged,
                    placeholder   = {
                        Text("Add tag…", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    singleLine      = true,
                    modifier        = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.addTag() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor   = Color.Transparent,
                        focusedTextColor     = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor   = MaterialTheme.colorScheme.onBackground,
                        cursorColor          = MaterialTheme.colorScheme.primary,
                    ),
                )
            }

            if (state.allTags.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.allTags.filterNot { it in state.tags }.take(10)) { tag ->
                        SuggestionChip(onClick = { vm.addTag(tag) }, label = { Text(tag) })
                    }
                }
            }

            // ── Folder picker ─────────────────────────────────────────────────
            Text("Folder", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            var expanded by remember { mutableStateOf(false) }
            val currentFolder = state.folders.find { it.id == state.folderId }

            ExposedDropdownMenuBox(
                expanded        = expanded,
                onExpandedChange = { expanded = it },
            ) {
                OutlinedTextField(
                    value         = currentFolder?.title ?: "No folder",
                    onValueChange = {},
                    readOnly      = true,
                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier      = Modifier.fillMaxWidth().menuAnchor(),
                    shape         = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor     = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor   = MaterialTheme.colorScheme.onBackground,
                    ),
                )
                ExposedDropdownMenu(
                    expanded        = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    DropdownMenuItem(
                        text        = { Text("No folder") },
                        onClick     = { vm.onFolderSelected(-1L); expanded = false },
                        leadingIcon = { Icon(Icons.Outlined.FolderOff, null,
                            modifier = Modifier.size(18.dp)) },
                    )
                    state.folders.forEach { folder ->
                        DropdownMenuItem(
                            text        = { Text(folder.title) },
                            onClick     = { vm.onFolderSelected(folder.id); expanded = false },
                            leadingIcon = { Icon(Icons.Outlined.Folder, null,
                                modifier = Modifier.size(18.dp)) },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text        = { Text("New folder…", color = MaterialTheme.colorScheme.primary) },
                        onClick     = { expanded = false; vm.showNewFolderDialog() },
                        leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)) },
                    )
                }
            }

            RemindMeButton(
                bookmarkTitle = state.title,
                bookmarkUrl   = state.url,
                tasksEnabled  = tasksEnabled,
                iconOnly      = false,
            )

            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun BookmarkTextField(
    value:        String,
    onValue:      (String) -> Unit,
    label:        String,
    placeholder:  String,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine:   Boolean = true,
    minLines:     Int = 1,
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValue,
        label         = { Text(label) },
        placeholder   = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        singleLine    = singleLine,
        minLines      = minLines,
        modifier      = Modifier.fillMaxWidth(),
        shape         = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedTextColor     = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor   = MaterialTheme.colorScheme.onBackground,
            cursorColor          = MaterialTheme.colorScheme.primary,
            focusedLabelColor    = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor  = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
