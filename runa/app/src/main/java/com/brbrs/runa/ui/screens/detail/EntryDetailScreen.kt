package com.brbrs.runa.ui.screens.detail

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.SubcomposeAsyncImage
import com.brbrs.runa.data.local.PhotoStorage
import com.brbrs.runa.data.repository.JournalEntry
import com.brbrs.runa.data.repository.JournalRepository
import com.brbrs.runa.R
import com.brbrs.runa.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class DetailUiState(
    val entry: JournalEntry? = null,
    val isLoading: Boolean   = true,
    val deleted: Boolean     = false,
)

@HiltViewModel
class EntryDetailViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
    private val photoStorage: PhotoStorage,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun loadEntry(id: String) {
        viewModelScope.launch {
            val entry = journalRepository.getById(id)
            _uiState.update { it.copy(entry = entry, isLoading = false) }
        }
    }

    fun deleteEntry() {
        val id = _uiState.value.entry?.id ?: return
        viewModelScope.launch {
            val paths = _uiState.value.entry?.photoPaths ?: emptyList()
            photoStorage.deletePhotos(paths)
            journalRepository.delete(id)
            _uiState.update { it.copy(deleted = true) }
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryDetailScreen(
    entryId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: EntryDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark  = LocalIsDark.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(entryId) { viewModel.loadEntry(entryId) }
    LaunchedEffect(uiState.deleted) { if (uiState.deleted) onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .runaBackground(isDark),
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color    = MaterialTheme.colorScheme.primary,
            )
        } else {
            val entry = uiState.entry
            if (entry == null) {
                onBack()
                return@Box
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {

                // ── Hero photo(s) ─────────────────────────────────────────────
                if (entry.photoPaths.isNotEmpty()) {
                    val pagerState = rememberPagerState(pageCount = { entry.photoPaths.size })

                    Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                        HorizontalPager(
                            state    = pagerState,
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            SubcomposeAsyncImage(
                                model              = File(entry.photoPaths[page]),
                                contentDescription = null,
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier.fillMaxSize(),
                            )
                        }

                        // Gradient overlay at bottom for legibility
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            if (isDark) DarkBackground else LightBackground,
                                        )
                                    )
                                )
                        )

                        // Page indicators
                        if (entry.photoPaths.size > 1) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                repeat(entry.photoPaths.size) { i ->
                                    Box(
                                        modifier = Modifier
                                            .size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (i == pagerState.currentPage)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                            )
                                    )
                                }
                            }
                        }

                        // Back button overlay
                        IconButton(
                            onClick  = onBack,
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(8.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                } else {
                    // No photos — just the top bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
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
                                .widthIn(max = 72.dp)
                                .padding(end = 8.dp),
                        )
                    }
                }

                // ── Entry content — nostalgic journal page ────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                ) {
                    Spacer(Modifier.height(if (entry.photoPaths.isEmpty()) 8.dp else 0.dp))

                    // Date — styled like a diary heading
                    val fullDate = remember(entry.entryDateTimeMs) {
                        SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date(entry.entryDateTimeMs))
                    }
                    val timeStr = remember(entry.entryDateTimeMs) {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(entry.entryDateTimeMs))
                    }

                    Text(
                        text      = fullDate,
                        style     = MaterialTheme.typography.labelLarge,
                        color     = MaterialTheme.colorScheme.primary,
                        fontStyle = FontStyle.Italic,
                    )
                    Text(
                        text  = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (entry.locationName != null) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                            Text(entry.locationName!!, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Title in DM Serif — the headline of this memory
                    if (entry.title.isNotBlank()) {
                        Text(
                            text  = entry.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    // Decorative rule — diary feel
                    HorizontalDivider(
                        color     = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        thickness = 1.dp,
                        modifier  = Modifier.padding(vertical = 4.dp),
                    )

                    Spacer(Modifier.height(16.dp))

                    // Body — generous line height, handwriting-esque
                    if (entry.body.isNotBlank()) {
                        Text(
                            text  = entry.body,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                lineHeight = 30.sp,
                                color      = MaterialTheme.colorScheme.onBackground,
                            ),
                        )
                    }

                    Spacer(Modifier.height(48.dp))

                    // ── Actions ───────────────────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    ) {
                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            colors  = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border  = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                            shape   = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Delete")
                        }
                        Button(
                            onClick = { onEdit(entryId) },
                            colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape   = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Edit")
                        }
                    }

                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title   = { Text("Delete this entry?") },
            text    = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteDialog = false; viewModel.deleteEntry() },
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}
