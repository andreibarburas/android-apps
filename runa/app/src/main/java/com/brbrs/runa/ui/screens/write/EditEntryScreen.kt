package com.brbrs.runa.ui.screens.write

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.runa.R
import com.brbrs.runa.data.local.PhotoStorage
import com.brbrs.runa.data.repository.LocationRepository
import com.brbrs.runa.data.repository.LocationResult
import com.brbrs.runa.ui.components.LocationPickerSheet
import com.brbrs.runa.data.repository.JournalEntry
import com.brbrs.runa.data.repository.JournalRepository
import com.brbrs.runa.data.repository.SyncRepository
import com.brbrs.runa.ui.components.PhotoPickerRow
import com.brbrs.runa.ui.theme.*
import com.brbrs.runa.ui.theme.DMSerifDisplayFamily
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class EditUiState(
    val entryId: String          = "",
    val title: String            = "",
    val body: String             = "",
    val photoPaths: List<String>  = emptyList(),
    val entryDateTimeMs: Long    = System.currentTimeMillis(),
    val locationName: String?    = null,
    val latitude: Double?        = null,
    val longitude: Double?       = null,
    val isLoaded: Boolean        = false,
    val isSaving: Boolean        = false,
    val saved: Boolean           = false,
    val error: String?           = null,
)

@HiltViewModel
class EditEntryViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
    private val photoStorage: PhotoStorage,
    private val syncRepository: SyncRepository,
    val locationRepository: LocationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    fun loadEntry(id: String) {
        viewModelScope.launch {
            val entry = journalRepository.getById(id) ?: return@launch
            _uiState.update {
                it.copy(
                    entryId         = entry.id,
                    title           = entry.title,
                    body            = entry.body,
                    photoPaths      = entry.photoPaths,
                    entryDateTimeMs = entry.entryDateTimeMs,
                    locationName    = entry.locationName,
                    latitude        = entry.latitude,
                    longitude       = entry.longitude,
                    isLoaded        = true,
                )
            }
        }
    }

    fun onTitleChanged(value: String) = _uiState.update { it.copy(title = value) }
    fun onBodyChanged(value: String)  = _uiState.update { it.copy(body  = value) }
    fun onDateTimeChanged(ms: Long)   = _uiState.update { it.copy(entryDateTimeMs = ms) }
    fun onLocationSelected(result: LocationResult) = _uiState.update {
        it.copy(locationName = result.shortName, latitude = result.latitude, longitude = result.longitude)
    }
    fun onLocationCleared() = _uiState.update { it.copy(locationName = null, latitude = null, longitude = null) }

    fun onPhotoPicked(uri: Uri, index: Int) {
        viewModelScope.launch {
            val previous = _uiState.value.photoPaths.getOrNull(index)
            val path = photoStorage.savePhoto(uri, _uiState.value.entryId, index, previous) ?: return@launch
            val current = _uiState.value.photoPaths.toMutableList()
            if (index < current.size) current[index] = path else current.add(path)
            _uiState.update { it.copy(photoPaths = current) }
        }
    }

    fun createCameraCapture(index: Int): PhotoStorage.CameraCapture =
        photoStorage.createCameraCaptureTarget(_uiState.value.entryId, index)

    fun onCameraCaptureComplete(capture: PhotoStorage.CameraCapture, index: Int) {
        viewModelScope.launch {
            val path = photoStorage.processCameraCapture(capture.file) ?: return@launch
            val current = _uiState.value.photoPaths.toMutableList()
            if (index < current.size) current[index] = path else current.add(path)
            _uiState.update { it.copy(photoPaths = current) }
        }
    }

    fun onRemovePhoto(index: Int) {
        viewModelScope.launch {
            val path = _uiState.value.photoPaths.getOrNull(index) ?: return@launch
            photoStorage.deletePhoto(path)
            val current = _uiState.value.photoPaths.toMutableList()
            current.removeAt(index)
            _uiState.update { it.copy(photoPaths = current) }
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.title.isBlank() && state.body.isBlank()) {
            _uiState.update { it.copy(error = "Write something first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            journalRepository.save(
                JournalEntry(
                    id              = state.entryId,
                    title           = state.title.trim(),
                    body            = state.body.trim(),
                    entryDateTimeMs = state.entryDateTimeMs,
                    createdAtMs     = System.currentTimeMillis(),
                    photoPaths      = state.photoPaths,
                    locationName    = state.locationName,
                    latitude        = state.latitude,
                    longitude       = state.longitude,
                )
            )
            // Auto-sync to Nextcloud (no-op if local mode)
            syncRepository.sync()
            _uiState.update { it.copy(isSaving = false, saved = true) }
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEntryScreen(
    entryId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditEntryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark  = LocalIsDark.current

    var showDatePicker     by remember { mutableStateOf(false) }
    var showTimePicker     by remember { mutableStateOf(false) }
    var showLocationPicker by remember { mutableStateOf(false) }

    LaunchedEffect(entryId) { viewModel.loadEntry(entryId) }
    LaunchedEffect(uiState.saved) { if (uiState.saved) onSaved() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                }
                Image(
                    painter            = painterResource(id = R.drawable.runa_wordmark),
                    contentDescription = "Runa",
                    colorFilter        = androidx.compose.ui.graphics.ColorFilter.tint(
                        if (isDark) androidx.compose.ui.graphics.Color.White
                        else MaterialTheme.colorScheme.primary
                    ),
                    modifier           = Modifier
                        .height(20.dp)
                        .widthIn(max = 72.dp),
                )
                IconButton(onClick = viewModel::save) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().runaBackground(isDark).padding(padding),
        ) {
            if (!uiState.isLoaded) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    Spacer(Modifier.height(12.dp))

                    // Date & time row
                    val dateLabel = remember(uiState.entryDateTimeMs) {
                        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(uiState.entryDateTimeMs))
                    }
                    val timeLabel = remember(uiState.entryDateTimeMs) {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(uiState.entryDateTimeMs))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AssistChip(
                            onClick = { showDatePicker = true },
                            label   = { Text(dateLabel, style = MaterialTheme.typography.labelLarge) },
                            leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            shape  = RoundedCornerShape(10.dp),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor     = MaterialTheme.colorScheme.onSurfaceVariant,
                                leadingIconContentColor = MaterialTheme.colorScheme.primary,
                            ),
                            border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = MaterialTheme.colorScheme.outline),
                        )
                        AssistChip(
                            onClick = { showTimePicker = true },
                            label   = { Text(timeLabel, style = MaterialTheme.typography.labelLarge) },
                            leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            shape  = RoundedCornerShape(10.dp),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor     = MaterialTheme.colorScheme.onSurfaceVariant,
                                leadingIconContentColor = MaterialTheme.colorScheme.primary,
                            ),
                            border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = MaterialTheme.colorScheme.outline),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    TextField(
                        value = uiState.title, onValueChange = viewModel::onTitleChanged,
                        placeholder = { Text("What happened…", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DMSerifDisplayFamily), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                        textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = DMSerifDisplayFamily, color = MaterialTheme.colorScheme.onBackground),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        singleLine = false, maxLines = 4,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 4.dp))

                    TextField(
                        value = uiState.body, onValueChange = viewModel::onBodyChanged,
                        placeholder = { Text("Write your thoughts…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        singleLine = false, minLines = 8,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(16.dp))
                    if (uiState.locationName != null) {
                        InputChip(
                            selected = true,
                            onClick  = {},
                            label    = { Text(uiState.locationName!!, style = MaterialTheme.typography.labelLarge) },
                            leadingIcon  = { Icon(Icons.Outlined.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            trailingIcon = {
                                IconButton(onClick = viewModel::onLocationCleared, modifier = Modifier.size(18.dp)) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                                }
                            },
                            shape  = RoundedCornerShape(10.dp),
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor   = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                selectedLabelColor       = MaterialTheme.colorScheme.primary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    } else {
                        TextButton(onClick = { showLocationPicker = true }) {
                            Icon(Icons.Outlined.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text("Add location", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Photos", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    PhotoPickerRow(
                        photoPaths = uiState.photoPaths,
                        onPhotoPicked = viewModel::onPhotoPicked,
                        onCreateCameraCapture = viewModel::createCameraCapture,
                        onCameraCaptureComplete = viewModel::onCameraCaptureComplete,
                        onRemovePhoto = viewModel::onRemovePhoto,
                    )
                    uiState.error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }

    if (showLocationPicker) {
        LocationPickerSheet(
            locationRepository = viewModel.locationRepository,
            onLocationSelected = viewModel::onLocationSelected,
            onDismiss          = { showLocationPicker = false },
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = uiState.entryDateTimeMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selectedMs = datePickerState.selectedDateMillis
                    if (selectedMs != null) {
                        val cal = Calendar.getInstance().apply { timeInMillis = uiState.entryDateTimeMs }
                        val selCal = Calendar.getInstance().apply { timeInMillis = selectedMs }
                        cal.set(Calendar.YEAR, selCal.get(Calendar.YEAR))
                        cal.set(Calendar.MONTH, selCal.get(Calendar.MONTH))
                        cal.set(Calendar.DAY_OF_MONTH, selCal.get(Calendar.DAY_OF_MONTH))
                        viewModel.onDateTimeChanged(cal.timeInMillis)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = uiState.entryDateTimeMs }
        val timePickerState = rememberTimePickerState(initialHour = cal.get(Calendar.HOUR_OF_DAY), initialMinute = cal.get(Calendar.MINUTE))
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Set time") },
            text  = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val updated = Calendar.getInstance().apply {
                        timeInMillis = uiState.entryDateTimeMs
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                    }
                    viewModel.onDateTimeChanged(updated.timeInMillis)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
        )
    }
}
