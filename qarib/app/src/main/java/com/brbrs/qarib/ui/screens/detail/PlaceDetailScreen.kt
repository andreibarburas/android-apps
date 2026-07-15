package com.brbrs.qarib.ui.screens.detail

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import com.brbrs.qarib.R
import com.brbrs.qarib.domain.model.Visit
import com.brbrs.qarib.ui.theme.LocalIsDark
import com.brbrs.qarib.ui.theme.categoryColor
import com.brbrs.qarib.ui.theme.icon
import com.brbrs.qarib.ui.theme.labelRes
import com.brbrs.qarib.ui.theme.qaribBackground
import com.brbrs.qarib.ui.theme.qaribCard
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDeleted: () -> Unit,
    viewModel: PlaceDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val visits by viewModel.visits.collectAsState()
    val isDark = LocalIsDark.current
    val context = LocalContext.current
    val place = uiState.place

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(place?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (place != null) {
                        IconButton(onClick = { onEdit(place.id) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.place_detail_edit))
                        }
                        IconButton(onClick = { viewModel.showDeleteConfirm(true) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.place_detail_delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .qaribBackground(isDark)
        ) {
            if (place == null) return@Box

            val accent = categoryColor(place.category)
            val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()) }
            val shortDateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Hero photo
                if (place.photoPath.isNotBlank()) {
                    item {
                        SubcomposeAsyncImage(
                            model = File(place.photoPath),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(20.dp)),
                            error = {},
                        )
                    }
                }

                // Category + address
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .qaribCard(isDark)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(accent.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(place.category.icon(), contentDescription = null, tint = accent)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(place.category.labelRes()),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = place.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.place_detail_added_on, dateFormatter.format(Date(place.createdAt))),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                }

                // Note
                if (place.note.isNotBlank()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .qaribCard(isDark)
                                .padding(16.dp),
                        ) {
                            Text(
                                text = place.note,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                // Actions row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {
                                val uri = Uri.parse("geo:${place.latitude},${place.longitude}?q=${place.latitude},${place.longitude}(${place.name})")
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        ) {
                            Icon(Icons.Outlined.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.place_detail_open_maps))
                        }

                        OutlinedButton(
                            onClick = { viewModel.toggleMuted() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = if (place.notificationsMuted) Icons.Outlined.NotificationsOff else Icons.Outlined.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (place.notificationsMuted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (place.notificationsMuted) stringResource(R.string.place_detail_unmute) else stringResource(R.string.place_detail_mute))
                        }
                    }
                }

                // Mark visited toggle
                item {
                    OutlinedButton(
                        onClick = { viewModel.toggleVisited() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = if (place.visited) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (place.visited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (place.visited) stringResource(R.string.place_detail_mark_not_visited) else stringResource(R.string.place_detail_mark_visited),
                            textDecoration = if (place.visited) TextDecoration.None else TextDecoration.None,
                        )
                    }
                }

                // Visit history header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.place_detail_visits_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        TextButton(onClick = { viewModel.startAddVisit() }) {
                            Icon(Icons.Outlined.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.place_detail_add_visit))
                        }
                    }
                }

                if (visits.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .qaribCard(isDark)
                                .padding(20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.place_detail_no_visits),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(visits, key = { it.id }) { visit ->
                        VisitCard(
                            visit = visit,
                            dateFormatter = shortDateFormatter,
                            isDark = isDark,
                            onEdit = { viewModel.startEditVisit(visit) },
                            onDelete = { viewModel.deleteVisit(visit.id) },
                        )
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    // Delete place confirmation
    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.showDeleteConfirm(false) },
            title = { Text(stringResource(R.string.place_detail_delete_confirm_title)) },
            text = { Text(stringResource(R.string.place_detail_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.showDeleteConfirm(false)
                    viewModel.deletePlace()
                    onDeleted()
                }) { Text(stringResource(R.string.place_detail_confirm_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showDeleteConfirm(false) }) {
                    Text(stringResource(R.string.place_detail_cancel))
                }
            },
        )
    }

    // Add/edit visit dialog
    if (uiState.showAddVisitSheet) {
        uiState.editingVisit?.let { visit ->
            val isEditing = visits.any { it.id == visit.id }
            AddVisitSheet(
                visit = visit,
                isEditing = isEditing,
                onDismiss = { viewModel.dismissVisitSheet() },
                onNoteChange = viewModel::updateEditingVisitNote,
                onDateChange = viewModel::updateEditingVisitDate,
                onAddPhoto = viewModel::addPhotoToEditingVisit,
                onRemovePhoto = viewModel::removePhotoFromEditingVisit,
                onSave = { viewModel.saveVisit() },
            )
        }
    }
}

@Composable
private fun VisitCard(
    visit: Visit,
    dateFormatter: SimpleDateFormat,
    isDark: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .qaribCard(isDark)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = dateFormatter.format(Date(visit.visitedAt)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.place_detail_edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        if (visit.note.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = visit.note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (visit.photoPaths.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visit.photoPaths) { path ->
                    SubcomposeAsyncImage(
                        model = File(path),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(90.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        error = {},
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.visit_delete_confirm_title)) },
            text = { Text(stringResource(R.string.visit_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text(stringResource(R.string.place_detail_confirm_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.place_detail_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddVisitSheet(
    visit: Visit,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onNoteChange: (String) -> Unit,
    onDateChange: (Long) -> Unit,
    onAddPhoto: (Uri) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onSave: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { onAddPhoto(it) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isEditing) R.string.visit_edit_title else R.string.place_detail_add_visit
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Date picker row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.visit_date_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = dateFormatter.format(Date(visit.visitedAt)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // Note field
                OutlinedTextField(
                    value = visit.note,
                    onValueChange = onNoteChange,
                    label = { Text(stringResource(R.string.visit_note_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                )

                // Photos
                if (visit.photoPaths.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(visit.photoPaths) { path ->
                            Box {
                                SubcomposeAsyncImage(
                                    model = File(path),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    error = {},
                                )
                                IconButton(
                                    onClick = { onRemovePhoto(path) },
                                    modifier = Modifier
                                        .size(22.dp)
                                        .align(Alignment.TopEnd)
                                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                                ) {
                                    Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }

                TextButton(
                    onClick = { photoPickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.visit_add_photo))
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = visit.visitedAt)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateChange(it) }
                    showDatePicker = false
                }) { Text(stringResource(R.string.dialog_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        ) {
            DatePicker(state = state)
        }
    }
}
