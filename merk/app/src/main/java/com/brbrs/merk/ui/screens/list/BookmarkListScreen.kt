package com.brbrs.merk.ui.screens.list

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.merk.data.local.BookmarkEntity
import com.brbrs.merk.ui.components.FaviconImage
import com.brbrs.merk.ui.components.RemindMeButton
import com.brbrs.merk.ui.theme.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BookmarkListScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpen: (Long) -> Unit,
    onSettings: () -> Unit,
    vm: BookmarkListViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsState()
    val tags  by vm.tags.collectAsState()
    val isDark = state.isDark

    val bgBrush = if (isDark)
        Brush.verticalGradient(listOf(NavyDeep, NavyMid, NavySurface))
    else
        Brush.verticalGradient(listOf(LightBg, LightSurface, LightSurface2))

    val glowColor = if (isDark) GlowRed else GlowRedLight

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Three-stop background ─────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize().background(bgBrush))

        // ── Radial glow at top ────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent),
                        radius = 600f,
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    MerkLogoHeader()
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${state.bookmarks.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "bookmarks${if (state.isSyncing) " · syncing..." else " · synced"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = vm::sync) {
                        if (state.isSyncing) {
                            CircularProgressIndicator(
                                color       = MaterialTheme.colorScheme.primary,
                                modifier    = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Outlined.Sync, "Sync",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = vm::toggleTheme) {
                        Icon(
                            if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = if (isDark) "Switch to light" else "Switch to dark",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Search pill with BasicTextField ───────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (isDark) Color.White.copy(alpha = 0.07f) else LightSurface
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Outlined.Search, null,
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (state.searchQuery.isEmpty()) {
                            Text(
                                "Search bookmarks...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        BasicTextField(
                            value         = state.searchQuery,
                            onValueChange = vm::onSearchChanged,
                            textStyle     = TextStyle(
                                color      = MaterialTheme.colorScheme.onBackground,
                                fontSize   = 14.sp,
                                fontFamily = FontFamily.SansSerif,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine  = true,
                            modifier    = Modifier.fillMaxWidth(),
                        )
                    }
                    if (state.searchQuery.isNotEmpty()) {
                        Icon(Icons.Outlined.Close, null,
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp).clickable { vm.onSearchChanged("") })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Tag chips ─────────────────────────────────────────────────────
            if (tags.isNotEmpty()) {
                LazyRow(
                    contentPadding        = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier              = Modifier.fillMaxWidth(),
                ) {
                    item {
                        TagChip("All", state.selectedTag == null, isDark) { vm.onTagSelected(null) }
                    }
                    items(tags) { tag ->
                        TagChip(tag, state.selectedTag == tag, isDark) { vm.onTagSelected(tag) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Sync error ────────────────────────────────────────────────────
            state.syncError?.let { err ->
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
                    IconButton(onClick = vm::dismissSyncError, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Outlined.Close, null, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // ── List ──────────────────────────────────────────────────────────
            if (state.bookmarks.isEmpty() && !state.isSyncing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.searchQuery.isNotBlank()) "No results" else "No bookmarks yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier            = Modifier.fillMaxSize(),
                ) {
                    // Section header with badge count
                    item {
                        SectionHeader(
                            label  = "All bookmarks",
                            count  = state.bookmarks.size,
                            isDark = isDark,
                        )
                    }
                    items(state.bookmarks, key = { it.id }) { bookmark ->
                        BookmarkCard(
                            bookmark     = bookmark,
                            tasksEnabled = state.tasksEnabled,
                            isDark       = isDark,
                            serverUrl    = state.serverUrl,
                            authHeader   = state.authHeader,
                            onClick      = { onOpen(bookmark.id) },
                            onEdit       = { onEdit(bookmark.id) },
                            onDelete     = { vm.onDeleteBookmark(bookmark.id) },
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        // ── FAB ───────────────────────────────────────────────────────────────
        FloatingActionButton(
            onClick        = onAdd,
            modifier       = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 24.dp, bottom = 24.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor   = Color.White,
            shape          = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Outlined.Add, "Add bookmark", modifier = Modifier.size(28.dp))
        }
    }
}

// ── Logo header (uses MerkLogo composable) ────────────────────────────────────

@Composable
private fun MerkLogoHeader() {
    com.brbrs.merk.ui.components.MerkLogo(modifier = Modifier.height(44.dp))
}

// ── Section header with badge count ──────────────────────────────────────────

@Composable
private fun SectionHeader(label: String, count: Int, isDark: Boolean) {
    Row(
        modifier              = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.15f else 0.10f))
                .padding(horizontal = 7.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ── Tag chip ──────────────────────────────────────────────────────────────────

@Composable
private fun TagChip(label: String, selected: Boolean, isDark: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .merkChip(isDark = isDark, selected = selected)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Bookmark card ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkCard(
    bookmark: BookmarkEntity,
    tasksEnabled: Boolean,
    isDark: Boolean,
    serverUrl: String,
    authHeader: String,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val tagList  = remember(bookmark.tags) {
        bookmark.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .merkCard(isDark = isDark)
            .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.Top,
        ) {
            // Favicon with initial fallback
            FaviconImage(
                bookmarkId = bookmark.id,
                serverUrl  = serverUrl,
                authHeader = authHeader,
                title      = bookmark.title.ifBlank { bookmark.url },
                size       = 38.dp,
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = bookmark.title.ifBlank { bookmark.url },
                    style    = MaterialTheme.typography.titleLarge,
                    color    = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text     = Uri.parse(bookmark.url).host ?: bookmark.url,
                    style    = MaterialTheme.typography.labelMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tagList.isNotEmpty()) {
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        tagList.take(4).forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.11f))
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    tag,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                )
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                RemindMeButton(
                    bookmarkTitle = bookmark.title,
                    bookmarkUrl   = bookmark.url,
                    tasksEnabled  = tasksEnabled,
                    iconOnly      = true,
                )
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.MoreVert, null,
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text        = { Text("Edit") },
                            onClick     = { showMenu = false; onEdit() },
                            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        )
                        DropdownMenuItem(
                            text        = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick     = { showMenu = false; onDelete() },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        )
                    }
                }
            }
        }
    }
}
