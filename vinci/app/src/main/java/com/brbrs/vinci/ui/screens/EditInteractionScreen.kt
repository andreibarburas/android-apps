package com.brbrs.vinci.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.res.painterResource
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.vinci.ui.components.ContactAvatar
import com.brbrs.vinci.ui.theme.*
import com.brbrs.vinci.ui.viewmodels.INTERACTION_OUTCOMES
import com.brbrs.vinci.ui.viewmodels.INTERACTION_REASONS
import com.brbrs.vinci.ui.viewmodels.INTERACTION_TYPES
import com.brbrs.vinci.ui.viewmodels.EditInteractionViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditInteractionScreen(
    logId: Long,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
    viewModel: EditInteractionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark  = LocalIsDark.current
    val context = LocalContext.current
    val tsFormat = SimpleDateFormat("yyyy/MM/dd | HH:mm", Locale.getDefault())

    LaunchedEffect(uiState.saved)   { if (uiState.saved)   onSaved() }
    LaunchedEffect(uiState.deleted) { if (uiState.deleted) onDeleted() }

    // Delete confirmation dialog
    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteConfirm,
            title = { Text("Delete this interaction?") },
            text  = { Text("This will remove it from Vinci. The entry on Nextcloud will be overwritten on next sync.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = viewModel::delete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteConfirm) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isDark) Brush.verticalGradient(listOf(NavyDeep, NavyMid))
                    else Brush.verticalGradient(listOf(LightBg, LightSurface2))
                )
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.statusBarsPadding().padding(start = 8.dp, top = 8.dp, end = 8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary)
                }
                Text("Edit interaction", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                IconButton(onClick = viewModel::showDeleteConfirm) {
                    Icon(Icons.Outlined.DeleteOutline, "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Contact summary card
            uiState.contact?.let { contact ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .glassCardPrimary(cornerRadius = 16.dp)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ContactAvatar(
                        photoUri    = contact.photoUri,
                        displayName = contact.displayName,
                        size        = 48,
                        shape       = CircleShape,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(contact.displayName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(tsFormat.format(Date(uiState.interactionTimestamp)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = uiState.interactionTimestamp }
                        DatePickerDialog(context, { _, year, month, day ->
                            TimePickerDialog(context, { _, hour, minute ->
                                val newCal = Calendar.getInstance().apply { set(year, month, day, hour, minute, 0) }
                                viewModel.onTimestampChanged(newCal.timeInMillis)
                            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                            .apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                    }) {
                        Icon(Icons.Outlined.EditCalendar, "Change date", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }

            if (uiState.isUnknownNumber) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .vinciCardElevated(isDark = isDark, cornerRadius = 16.dp)
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                                .background(CyanPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.PersonOff, null, tint = CyanPrimary, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(uiState.log?.phoneNumber.orEmpty().ifBlank { "Unknown number" },
                                style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text(tsFormat.format(Date(uiState.interactionTimestamp)),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Label", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = uiState.contactNameInput,
                        onValueChange = viewModel::onContactNameInputChanged,
                        placeholder = { Text("e.g. Insurance company, Recruiter...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor     = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor   = MaterialTheme.colorScheme.onBackground,
                            cursorColor          = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {

                EditFormSection(label = "Type") {
                    EditChipRow(options = INTERACTION_TYPES, selected = uiState.interactionType, onSelect = viewModel::onInteractionTypeSelected)
                }

                // Social platform picker — shown below type when "Social Media" is selected
                if (uiState.interactionType == "Social Media") {
                    val platforms = uiState.availableSocialTypes.ifEmpty {
                        com.brbrs.vinci.ui.components.SOCIAL_PLATFORMS
                            .filter { it.key != "other" && it.key != "nextcloud" && it.drawableRes != 0 }
                            .map { it.key }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        platforms.forEach { platform ->
                            val sp = com.brbrs.vinci.ui.components.platformForKey(platform)
                            val isSelected = uiState.selectedSocialPlatform == platform
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .vinciChip(isDark = isDark, selected = isSelected)
                                    .clickable { viewModel.onSocialPlatformSelected(platform) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            ) {
                                if (sp.drawableRes != 0) {
                                    Icon(
                                        painter = painterResource(id = sp.drawableRes),
                                        contentDescription = null,
                                        tint = if (isSelected) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(sp.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSelected) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                EditFormSection(label = "People") {
                    com.brbrs.vinci.ui.components.ParticipantsPicker(
                        participants  = uiState.participants,
                        query         = uiState.participantQuery,
                        searchResults = uiState.participantSearchResults,
                        isDark        = isDark,
                        photosByUid   = uiState.participantPhotos,
                        onQueryChanged = viewModel::onParticipantQueryChanged,
                        onAddContact   = viewModel::addParticipantContact,
                        onAddFreeText  = viewModel::addParticipantFreeText,
                        onRemove       = viewModel::removeParticipant,
                    )
                }

                EditFormSection(label = "Reason") {
                    EditChipRow(options = INTERACTION_REASONS, selected = uiState.reason, onSelect = viewModel::onReasonSelected)
                }

                EditFormSection(label = "Notes") {
                    OutlinedTextField(
                        value = uiState.notes,
                        onValueChange = viewModel::onNotesChanged,
                        placeholder = { Text("What happened?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        minLines = 4,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor    = MaterialTheme.colorScheme.outline,
                            focusedTextColor        = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor      = MaterialTheme.colorScheme.onBackground,
                            cursorColor             = MaterialTheme.colorScheme.primary,
                            focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        ),
                    )
                }

                EditFormSection(label = "Outcome") {
                    EditChipRow(options = INTERACTION_OUTCOMES, selected = uiState.outcome, onSelect = viewModel::onOutcomeSelected)
                }

                EditFormSection(label = "Tags") {
                    var newTag by remember { mutableStateOf("") }
                    if (uiState.availableTags.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.availableTags.forEach { tag ->
                                val isSelected = tag in uiState.selectedTags
                                Box(
                                    modifier = Modifier
                                        .vinciChip(isDark = isDark, selected = isSelected)
                                        .clickable { viewModel.toggleTag(tag) }
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                ) {
                                    Text(tag, style = MaterialTheme.typography.labelLarge,
                                        color = if (isSelected) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newTag,
                            onValueChange = { newTag = it },
                            placeholder = { Text("New tag...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor     = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor   = MaterialTheme.colorScheme.onBackground,
                                cursorColor          = MaterialTheme.colorScheme.primary,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = {
                            if (newTag.isNotBlank()) { viewModel.toggleTag(newTag); newTag = "" }
                        }) {
                            Icon(Icons.Outlined.Add, "Add tag", tint = CyanPrimary)
                        }
                    }
                }

                EditFormSection(label = "Attachments") {
                    AttachmentsSection(
                        attachments = uiState.attachments,
                        isUploading = uiState.isUploadingAttachment,
                        isDark      = isDark,
                        onPick      = { uris -> viewModel.attachFiles(uris) },
                        onOpen      = { att -> viewModel.openAttachment(att) { uri ->
                            if (uri != null) {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, att.mimeType.ifBlank { "*/*" })
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(intent) }
                            }
                        } },
                        onDelete    = { att -> viewModel.deleteAttachment(att) },
                    )
                    if (uiState.attachmentError != null) {
                        Text(uiState.attachmentError!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }

                val cardTint = if (isDark) Color.White else Color.Black
                Box(modifier = Modifier.fillMaxWidth().glassCard(cornerRadius = 14.dp, tint = cardTint)) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.CalendarMonth, null, tint = AmberWarn, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Follow-up reminder", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Switch(
                                checked = uiState.followUpEnabled,
                                onCheckedChange = viewModel::onFollowUpToggled,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor   = AmberWarn,
                                    checkedTrackColor   = AmberWarn.copy(alpha = 0.3f),
                                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            )
                        }
                        if (uiState.followUpEnabled) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Follow up in", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(1, 3, 7, 14).forEach { days ->
                                        val selected = uiState.followUpDays == days
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (selected) AmberWarn.copy(0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
                                                .clickable { viewModel.onFollowUpDaysChanged(days) }
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                        ) {
                                            Text("${days}d", style = MaterialTheme.typography.bodyMedium, color = if (selected) AmberWarn else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = viewModel::showDeleteConfirm,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete")
                    }
                    Button(
                        onClick = viewModel::save,
                        enabled = !uiState.isSaving,
                        modifier = Modifier.weight(2f).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(color = NavyDeep, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.CloudUpload, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Save to Nextcloud", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun EditFormSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = CyanPrimary.copy(alpha = 0.6f))
        content()
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun EditChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = selected == option
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) CyanPrimary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(option, style = MaterialTheme.typography.bodyMedium, color = if (isSelected) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AttachmentsSection(
    attachments: List<com.brbrs.vinci.data.AttachmentEntity>,
    isUploading: Boolean,
    isDark: Boolean,
    onPick: (List<android.net.Uri>) -> Unit,
    onOpen: (com.brbrs.vinci.data.AttachmentEntity) -> Unit,
    onDelete: (com.brbrs.vinci.data.AttachmentEntity) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) onPick(uris) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        attachments.forEach { att ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .vinciCard(isDark = isDark)
                    .clickable { onOpen(att) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Icon(attachmentIcon(att.mimeType, att.fileName), null,
                    tint = CyanPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(att.fileName, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(formatFileSize(att.sizeBytes), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    imageVector = if (att.cachedLocally) Icons.Outlined.DownloadDone else Icons.Outlined.CloudQueue,
                    contentDescription = if (att.cachedLocally) "On device" else "Nextcloud only",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(10.dp))
                IconButton(onClick = { onDelete(att) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.Close, "Remove attachment",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                }
            }
        }

        OutlinedButton(
            onClick = { launcher.launch(arrayOf("*/*")) },
            enabled = !isUploading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (isUploading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = CyanPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Uploading...")
            } else {
                Icon(Icons.Outlined.AttachFile, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Attach file")
            }
        }
    }
}

private fun attachmentIcon(mimeType: String, fileName: String): androidx.compose.ui.graphics.vector.ImageVector = when {
    mimeType.startsWith("image/")          -> Icons.Outlined.Image
    mimeType == "application/pdf"          -> Icons.Outlined.PictureAsPdf
    mimeType.startsWith("audio/")          -> Icons.Outlined.AudioFile
    mimeType.startsWith("video/")          -> Icons.Outlined.VideoFile
    fileName.endsWith(".doc") || fileName.endsWith(".docx")  -> Icons.Outlined.Description
    fileName.endsWith(".xls") || fileName.endsWith(".xlsx")  -> Icons.Outlined.TableChart
    else                                    -> Icons.Outlined.InsertDriveFile
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024        -> String.format("%.0f KB", bytes / 1024.0)
    else                 -> "$bytes B"
}
