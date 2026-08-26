package com.brbrs.nota.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.brbrs.nota.ui.theme.DMSerifDisplayFontFamily
import com.brbrs.nota.ui.theme.InterTightFontFamily
import com.brbrs.nota.ui.theme.LocalCustomFontEnabled
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.nota.R
import com.brbrs.nota.data.NoteEntity
import com.brbrs.nota.ui.components.AddToTasksButton
import com.brbrs.nota.ui.components.NoteImageStrip
import com.brbrs.nota.ui.theme.*
import com.brbrs.nota.ui.viewmodels.NotesListViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    onNoteClick: (Long) -> Unit,
    onNewNote: (String) -> Unit,
    onSettings: () -> Unit,
    viewModel: NotesListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val imageLoader by viewModel.imageLoader.collectAsState()
    val isDark by viewModel.isDark.collectAsState()
    val tasksEnabled by viewModel.tasksEnabled.collectAsState()
    val compactMode by viewModel.compactMode.collectAsState()
    val customFont = LocalCustomFontEnabled.current
    val noteTitleFont = if (customFont) DMSerifDisplayFontFamily else FontFamily.Serif
    val noteBodyFont  = if (customFont) InterTightFontFamily     else FontFamily.SansSerif

    // Radial glow brush — green in dark, soft green in light
    val radialGlow = remember(isDark) {
        object : ShaderBrush() {
            override fun createShader(size: androidx.compose.ui.geometry.Size) =
                RadialGradientShader(
                    center = androidx.compose.ui.geometry.Offset(size.width / 2f, 0f),
                    radius = size.width * 0.75f,
                    colors = if (isDark)
                        listOf(Color(0x2200C853), Color.Transparent)
                    else
                        listOf(Color(0x1A4CAF78), Color.Transparent),
                )
        }
    }

    // Three-stop vertical background gradient
    val bgGradient = if (isDark)
        Brush.verticalGradient(listOf(NavyDeep, NavyMid, NavyDeep))
    else
        Brush.verticalGradient(listOf(LightBg, LightSurface, LightBg))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient),
    ) {
        // Radial glow overlay at top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(radialGlow),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top bar ──────────────────────────────────────────────────────
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
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.nota_logo),
                        contentDescription = "Nóta",
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                        modifier = Modifier.height(40.dp),
                    )
                    Text(
                        if (uiState.isSyncing)
                            stringResource(R.string.notes_count_syncing, uiState.notes.size)
                        else
                            stringResource(R.string.notes_count_synced, uiState.notes.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    if (uiState.isSyncing) {
                        CircularProgressIndicator(
                            color = GreenPrimary,
                            modifier = Modifier.size(20.dp).align(Alignment.CenterVertically),
                            strokeWidth = 2.dp,
                        )
                    }
                    IconButton(onClick = viewModel::sync) {
                        Icon(Icons.Outlined.Sync, "Sync", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = viewModel::toggleCompactMode) {
                        Icon(
                            if (compactMode) Icons.Outlined.ViewAgenda else Icons.Outlined.ViewStream,
                            contentDescription = if (compactMode) "Show previews" else "Compact view",
                            tint = if (compactMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = viewModel::toggleTheme) {
                        Icon(
                            if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = if (isDark) "Switch to light mode" else "Switch to dark mode",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Pill search bar ───────────────────────────────────────────────
            val searchBg = if (isDark) NavySurface else LightSurface2
            val searchBorder = if (isDark) GlassBorder else LightBorderSoft
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(searchBg)
                    .then(
                        Modifier.then(
                            androidx.compose.ui.Modifier.clip(RoundedCornerShape(24.dp))
                        )
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Search,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp).size(20.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                ) {
                    if (uiState.searchQuery.isEmpty()) {
                        Text(
                            stringResource(R.string.search_notes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BasicTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchChanged,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontFamily = noteBodyFont,
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchChanged("") }) {
                        Icon(Icons.Outlined.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Category chips ────────────────────────────────────────────────
            if (uiState.categories.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    item {
                        CategoryChip(
                            label = stringResource(R.string.category_all),
                            selected = uiState.selectedCategory == null,
                            isDark = isDark,
                            onClick = { viewModel.onCategorySelected(null) },
                        )
                    }
                    items(uiState.categories) { cat ->
                        CategoryChip(
                            label = cat,
                            selected = uiState.selectedCategory == cat,
                            isDark = isDark,
                            onClick = { viewModel.onCategorySelected(cat) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Notes list ────────────────────────────────────────────────────
            PullToRefreshBox(
                isRefreshing = uiState.isSyncing,
                onRefresh = viewModel::sync,
                modifier = Modifier.fillMaxSize(),
            ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                val pinned = uiState.notes.filter { it.favorite }
                val regular = uiState.notes.filter { !it.favorite }

                if (pinned.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.section_pinned), count = pinned.size, isDark = isDark) }
                    items(pinned, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            isPrimary = true,
                            isDark = isDark,
                            compactMode = compactMode,
                            imageLoader = imageLoader,
                            tasksEnabled = tasksEnabled,
                            onClick = { onNoteClick(note.id) },
                        )
                    }
                    item { SectionLabel(stringResource(R.string.section_recent), count = regular.size, isDark = isDark) }
                }

                items(regular, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        isPrimary = false,
                        isDark = isDark,
                        compactMode = compactMode,
                        imageLoader = imageLoader,
                        tasksEnabled = tasksEnabled,
                        onClick = { onNoteClick(note.id) },
                    )
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
            }
        }

        // ── FAB ──────────────────────────────────────────────────────────────
        FloatingActionButton(
            onClick = { onNewNote(uiState.selectedCategory ?: "") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 24.dp, bottom = 24.dp),
            containerColor = GreenPrimary,
            contentColor = NavyDeep,
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Outlined.Add, stringResource(R.string.new_note), modifier = Modifier.size(28.dp))
        }
    }
}

// ── Section header with badge count ──────────────────────────────────────────

@Composable
private fun SectionLabel(text: String, count: Int = 0, isDark: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = GreenPrimary.copy(alpha = 0.7f),
        )
        if (count > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(GreenPrimary.copy(alpha = if (isDark) 0.18f else 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = GreenPrimary,
                )
            }
        }
    }
}

// ── Category chip using vinciChip modifier ────────────────────────────────────

@Composable
private fun CategoryChip(label: String, selected: Boolean, isDark: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .vinciChip(isDark = isDark, selected = selected)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) GreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Note card ─────────────────────────────────────────────────────────────────

@Composable
private fun NoteCard(
    note: NoteEntity,
    isPrimary: Boolean,
    isDark: Boolean,
    compactMode: Boolean,
    imageLoader: coil.ImageLoader?,
    tasksEnabled: Boolean,
    onClick: () -> Unit,
) {
    val cardModifier = if (isPrimary)
        Modifier.fillMaxWidth().vinciCardElevated(isDark = isDark)
    else
        Modifier.fillMaxWidth().vinciCard(isDark = isDark)

    val customFont = LocalCustomFontEnabled.current
    val noteTitleFont = if (customFont) DMSerifDisplayFontFamily else null

    Box(
        modifier = cardModifier
            .clickable(onClick = onClick)
            .padding(
                horizontal = 18.dp,
                vertical = if (compactMode) 10.dp else 14.dp,
            ),
    ) {
        if (compactMode) {
            // ── Compact: title + category + date ──────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (note.isLocked) {
                        Icon(Icons.Outlined.Lock, null, tint = GreenPrimary.copy(alpha = 0.5f), modifier = Modifier.size(13.dp))
                    }
                    if (note.favorite) {
                        Icon(Icons.Filled.PushPin, null, tint = GreenPrimary.copy(alpha = 0.6f), modifier = Modifier.size(13.dp))
                    }
                    Text(
                        note.title.ifBlank { stringResource(R.string.untitled) },
                        style = if (noteTitleFont != null)
                            MaterialTheme.typography.titleMedium.copy(fontFamily = noteTitleFont)
                        else
                            MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (note.category.isNotBlank()) {
                        CategoryBadge(category = note.category, isDark = isDark)
                    }
                    Text(
                        formatTimestamp(note.modified),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            // ── Full: existing card layout ─────────────────────────────────
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        note.title.ifBlank { stringResource(R.string.untitled) },
                        style = if (noteTitleFont != null)
                            MaterialTheme.typography.titleLarge.copy(fontFamily = noteTitleFont)
                        else
                            MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (note.favorite) {
                        Icon(
                            Icons.Filled.PushPin,
                            null,
                            tint = GreenPrimary.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp).padding(start = 4.dp),
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                if (note.isLocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(GreenPrimary.copy(alpha = 0.05f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Lock,
                                contentDescription = "Locked",
                                tint = GreenPrimary.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                stringResource(R.string.protected_note),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    val previewText = note.content
                        .replace(Regex("#+\\s*"), "")
                        .replace(Regex("""!\[[^\]]*\]\([^)]+\)"""), stringResource(R.string.image_placeholder))
                        .lines()
                        .dropWhile { it.trim() == note.title.trim() || it.isBlank() }
                        .joinToString(" ")
                        .trim()
                        .take(120)
                    if (previewText.isNotBlank()) {
                        Text(
                            previewText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    NoteImageStrip(
                        markdown = note.content,
                        imageLoader = imageLoader,
                        maxImages = 1,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatTimestamp(note.modified),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AddToTasksButton(
                            noteTitle    = note.title,
                            noteContent  = note.content,
                            tasksEnabled = tasksEnabled,
                            iconOnly     = true,
                        )
                        if (note.category.isNotBlank()) {
                            CategoryBadge(category = note.category, isDark = isDark)
                        }
                    }
                }
            }
        }
    }
}

// ── Category badge ────────────────────────────────────────────────────────────

@Composable
private fun CategoryBadge(category: String, isDark: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(GreenPrimary.copy(alpha = if (isDark) 0.15f else 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            category,
            style = MaterialTheme.typography.labelSmall,
            color = GreenPrimary,
        )
    }
}

private fun formatTimestamp(epochSeconds: Long): String {
    val date = Date(epochSeconds * 1000)
    val datePart = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(date)
    val timePart = SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    return "$datePart | $timePart"
}
