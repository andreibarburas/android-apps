package com.brbrs.vinci.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.vinci.ui.components.*
import com.brbrs.vinci.ui.theme.*
import com.brbrs.vinci.ui.viewmodels.ContactsTabViewModel
import kotlinx.coroutines.launch

@Composable
fun ContactsTabScreen(
    onContactClick: (Long) -> Unit,
    onLogCall: (Long, String) -> Unit,
    onAddContact: () -> Unit = {},
    viewModel: ContactsTabViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isDark = LocalIsDark.current
    val listState: LazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDark)
                    Brush.verticalGradient(0f to NavyDeep, 0.4f to NavyMid, 1f to NavyDeep)
                else
                    Brush.verticalGradient(0f to LightBg, 0.4f to Color(0xFFEDE6F8), 1f to LightSurface2)
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        listOf(if (isDark) GlowPurple else LightGlow, Color.Transparent),
                        radius = if (isDark) 600f else 700f,
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {

            // -- Top bar --
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (uiState.selectionMode) {
                    Text("${uiState.selectedIds.size} selected", style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground)
                    Row {
                        TopBarIcon(Icons.Outlined.Star, "Star selected", active = false, isDark = isDark) { viewModel.starSelected() }
                        Spacer(Modifier.width(8.dp))
                        TopBarIcon(Icons.Outlined.StarOutline, "Unstar selected", active = false, isDark = isDark) { viewModel.unstarSelected() }
                        Spacer(Modifier.width(8.dp))
                        TopBarIcon(Icons.Outlined.Close, "Cancel selection", active = false, isDark = isDark) { viewModel.exitSelectionMode() }
                    }
                } else {
                    Text("Contacts", style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground)
                    TopBarIcon(
                        if (uiState.isGridView) Icons.Outlined.ViewList else Icons.Outlined.GridView,
                        "Toggle view", active = false, isDark = isDark,
                    ) { viewModel.toggleGridView() }
                }
            }

            // -- Search bar --
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(48.dp)
                    .then(
                        if (isDark)
                            Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White.copy(alpha = 0.07f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                        else
                            Modifier
                                .shadow(3.dp, RoundedCornerShape(24.dp),
                                    ambientColor = Color(0x2E9C27B0), spotColor = Color(0x149C27B0))
                                .clip(RoundedCornerShape(24.dp))
                                .background(LightSurface)
                                .border(1.5.dp, LightBorderMed, RoundedCornerShape(24.dp))
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchChanged,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box {
                                if (uiState.searchQuery.isEmpty()) {
                                    Text("Search contacts...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyLarge)
                                }
                                inner()
                            }
                        },
                    )
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchChanged("") }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Close, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // -- Tag filter --
            if (uiState.availableTags.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(uiState.availableTags) { tag ->
                        val isSelected = tag == uiState.selectedTag
                        Box(
                            modifier = Modifier
                                .vinciChip(isDark = isDark, selected = isSelected)
                                .clickable { viewModel.selectTagFilter(if (isSelected) null else tag) }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            Text(tag, style = MaterialTheme.typography.labelLarge,
                                color = if (isSelected) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // -- Content --
            Box(modifier = Modifier.fillMaxSize()) {

                if (uiState.searchQuery.isNotBlank()) {
                    // Flat search results
                    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
                        item { SectionHeader(text = "Results", badge = searchResults.size, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(searchResults, key = { it.id }) { contact ->
                            ContactListCard(contact = contact, isDark = isDark, accentColor = null, tag = null,
                                onClick = { onContactClick(contact.id) },
                                onLogCall = { onLogCall(contact.id, contact.phoneNumber) },
                                selectionMode = uiState.selectionMode,
                                selected = contact.id in uiState.selectedIds,
                                onLongClick = {
                                    if (uiState.selectionMode) viewModel.toggleSelected(contact.id)
                                    else viewModel.enterSelectionMode(contact.id)
                                })
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                } else if (uiState.isGridView) {
                    // Grid view -- grouped by letter (rows of 3), with sidebar index
                    val letterIndices = remember(uiState.groups) {
                        val map = mutableMapOf<String, Int>()
                        var idx = 0
                        uiState.groups.forEach { (letter, contacts) ->
                            map[letter] = idx
                            idx += 1 + ((contacts.size + 2) / 3) // header + row-chunks of 3
                        }
                        map
                    }

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 96.dp, end = 28.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        uiState.groups.forEach { (letter, contacts) ->
                            item(key = "gheader_$letter") {
                                SectionHeader(text = letter, badge = contacts.size, color = CyanPrimary)
                            }
                            items(contacts.chunked(3), key = { row -> "g_" + row.joinToString("_") { it.id.toString() } }) { row ->
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    row.forEach { contact ->
                                        ContactGridCell(contact = contact, isDark = isDark,
                                            modifier = Modifier.weight(1f)) { onContactClick(contact.id) }
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }

                    if (uiState.availableLetters.isNotEmpty()) {
                        AlphabetSidebar(
                            letters = uiState.availableLetters,
                            isDark  = isDark,
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp).zIndex(10f),
                            onLetterSelected = { letter ->
                                letterIndices[letter]?.let { index ->
                                    scope.launch { listState.scrollToItem(index) }
                                }
                            },
                        )
                    }
                } else {
                    // List view -- grouped by letter, with sidebar index
                    // Compute the scroll index (item index) for each letter's header
                    val letterIndices = remember(uiState.groups) {
                        val map = mutableMapOf<String, Int>()
                        var idx = 0
                        uiState.groups.forEach { (letter, contacts) ->
                            map[letter] = idx
                            idx += 1 + contacts.size // header + contacts
                        }
                        map
                    }

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 32.dp, end = 28.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        uiState.groups.forEach { (letter, contacts) ->
                            item(key = "header_$letter") {
                                SectionHeader(text = letter, badge = contacts.size, color = CyanPrimary)
                            }
                            items(contacts, key = { it.id }) { contact ->
                                ContactListCard(contact = contact, isDark = isDark, accentColor = null, tag = null,
                                    onClick = { onContactClick(contact.id) },
                                    onLogCall = { onLogCall(contact.id, contact.phoneNumber) },
                                    selectionMode = uiState.selectionMode,
                                    selected = contact.id in uiState.selectedIds,
                                    onLongClick = {
                                        if (uiState.selectionMode) viewModel.toggleSelected(contact.id)
                                        else viewModel.enterSelectionMode(contact.id)
                                    })
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }

                    // A-Z sidebar
                    if (uiState.availableLetters.isNotEmpty()) {
                        AlphabetSidebar(
                            letters = uiState.availableLetters,
                            isDark  = isDark,
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp).zIndex(10f),
                            onLetterSelected = { letter ->
                                letterIndices[letter]?.let { index ->
                                    scope.launch { listState.scrollToItem(index) }
                                }
                            },
                        )
                    }
                }
            }
        }

        // FAB -- add new contact
        FloatingActionButton(
            onClick = onAddContact,
            containerColor = CyanPrimary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 96.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "New contact")
        }
    }
}

// -- A-Z sidebar --

@Composable
private fun AlphabetSidebar(
    letters: List<String>,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onLetterSelected: (String) -> Unit,
) {
    Column(
        modifier = modifier
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(50.dp))
            .background(
                if (isDark) Color.White.copy(alpha = 0.05f)
                else Color(0xFF9C27B0).copy(alpha = 0.06f)
            )
            .padding(vertical = 6.dp, horizontal = 3.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            Box(
                modifier = Modifier
                    .size(width = 24.dp, height = 18.dp)
                    .clickable { onLetterSelected(letter) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = CyanPrimary.copy(alpha = 0.75f),
                )
            }
        }
    }
}
