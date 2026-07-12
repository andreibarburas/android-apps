package com.brbrs.vinci.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.vinci.R
import com.brbrs.vinci.ui.components.*
import com.brbrs.vinci.ui.theme.*
import com.brbrs.vinci.ui.viewmodels.ContactsListViewModel

@Composable
fun HomeScreen(
    onContactClick: (Long) -> Unit,
    onLogCall: (Long, String) -> Unit,
    onEditInteraction: (Long) -> Unit,
    onSettings: () -> Unit,
    viewModel: ContactsListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark  by viewModel.isDark.collectAsState()

    // Background — atmospheric gradient with subtle glow from top
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
        // Atmospheric glow at top — purple in dark, lavender in light
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
                Column {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.vinci_logo),
                        contentDescription = "Vinci",
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                        modifier = Modifier.height(36.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        buildString {
                            append("${uiState.allContacts.size} contacts")
                            if (uiState.isSyncing) append(" · syncing...")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (uiState.isSyncing) {
                        CircularProgressIndicator(color = CyanPrimary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(2.dp))
                    }
                    TopBarIcon(Icons.Outlined.EventNote, "Recent interactions",
                        active = uiState.showRecentInteractions, isDark = isDark) { viewModel.toggleShowRecentInteractions() }
                    TopBarIcon(if (uiState.showRecentCalls) Icons.Outlined.History else Icons.Outlined.HistoryToggleOff,
                        "Recent calls", active = uiState.showRecentCalls, isDark = isDark) { viewModel.toggleShowRecent() }
                    TopBarIcon(Icons.Outlined.Sync, "Sync", active = false, isDark = isDark) { viewModel.sync() }
                    TopBarIcon(if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                        "Theme", active = false, isDark = isDark) { viewModel.toggleTheme() }
                    TopBarIcon(Icons.Outlined.Settings, "Settings", active = false, isDark = isDark) { onSettings() }
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
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
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
                        IconButton(onClick = { viewModel.onSearchChanged("") },
                            modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Close, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // -- Content --
            LazyColumn(
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.fillMaxSize(),
            ) {

                // 1. Follow-ups due
                if (uiState.followUpContacts.isNotEmpty() && uiState.searchQuery.isBlank()) {
                    item {
                        SectionHeader(text = "Follow-ups due", badge = uiState.followUpContacts.size, color = AmberWarn)
                    }
                    items(uiState.followUpContacts, key = { "f_${it.id}" }) { contact ->
                        ContactListCard(
                            contact     = contact,
                            isDark      = isDark,
                            accentColor = AmberWarn,
                            tag         = "Due",
                            onClick     = { onContactClick(contact.id) },
                            onLogCall   = { onLogCall(contact.id, contact.phoneNumber) },
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                // 2. Starred -- 4-column grid
                if (uiState.starredContacts.isNotEmpty() && uiState.searchQuery.isBlank()) {
                    item { SectionHeader(text = "Starred", badge = null, color = CyanPrimary) }
                    val chunks = uiState.starredContacts.chunked(4)
                    items(chunks) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { contact ->
                                StarredGridCell(contact = contact, isDark = isDark,
                                    modifier = Modifier.weight(1f)) { onContactClick(contact.id) }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }

                // 3. Recent Vinci interactions
                if (uiState.recentInteractions.isNotEmpty() && uiState.showRecentInteractions && uiState.searchQuery.isBlank()) {
                    item { SectionHeader(text = "Recent interactions", badge = null, color = CyanPrimary) }
                    val visible = if (uiState.showAllRecentInteractions) uiState.recentInteractions else uiState.recentInteractions.take(5)
                    items(visible, key = { "i_${it.id}" }) { log ->
                        RecentInteractionCard(log = log, isDark = isDark) { onEditInteraction(log.id) }
                    }
                    if (uiState.recentInteractions.size > 5) {
                        item {
                            ShowMoreButton(
                                expanded = uiState.showAllRecentInteractions,
                                count    = uiState.recentInteractions.size,
                                label    = "interactions",
                                isDark   = isDark,
                                onClick  = viewModel::toggleShowAllRecentInteractions,
                            )
                        }
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }

                // 4. Recent calls -- split into "People" (contacts) and "Numbers" (not in contacts)
                if (uiState.recentContacts.isNotEmpty() && uiState.showRecentCalls && uiState.searchQuery.isBlank()) {
                    item { SectionHeader(text = "People", badge = uiState.recentContacts.size, color = CyanPrimary) }
                    val visible = if (uiState.showAllRecent) uiState.recentContacts else uiState.recentContacts.take(5)
                    items(visible, key = { "r_${it.id}" }) { contact ->
                        ContactListCard(contact = contact, isDark = isDark, accentColor = null,
                            tag = null, onClick = { onContactClick(contact.id) },
                            onLogCall = { onLogCall(contact.id, contact.phoneNumber) })
                    }
                    if (uiState.recentContacts.size > 5) {
                        item {
                            ShowMoreButton(expanded = uiState.showAllRecent,
                                count = uiState.recentContacts.size, label = "recent",
                                isDark = isDark, onClick = viewModel::toggleShowAllRecent)
                        }
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }

                if (uiState.recentUnknownCalls.isNotEmpty() && uiState.showRecentCalls && uiState.searchQuery.isBlank()) {
                    item { SectionHeader(text = "Numbers", badge = uiState.recentUnknownCalls.size, color = CyanPrimary) }
                    items(uiState.recentUnknownCalls, key = { "u_${it.phoneNumber}_${it.timestamp}" }) { call ->
                        UnknownCallCard(
                            phoneNumber     = call.phoneNumber,
                            timestamp       = call.timestamp,
                            isOutgoing      = call.isOutgoing,
                            durationSeconds = call.durationSeconds,
                            isDark          = isDark,
                            onLogCall       = { onLogCall(-1L, call.phoneNumber) },
                        )
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }

                // 5. Search results -- when actively searching
                if (uiState.searchQuery.isNotBlank()) {
                    item { SectionHeader(text = "Results", badge = uiState.allContacts.size, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(uiState.allContacts, key = { "s_${it.id}" }) { contact ->
                        ContactListCard(contact = contact, isDark = isDark, accentColor = null,
                            tag = null, onClick = { onContactClick(contact.id) },
                            onLogCall = { onLogCall(contact.id, contact.phoneNumber) })
                    }
                }

                // Birthdays this week
                if (uiState.upcomingBirthdays.isNotEmpty() && uiState.searchQuery.isBlank()) {
                    item { SectionHeader(text = "Birthdays this week", badge = uiState.upcomingBirthdays.size, color = PurplePrimary) }
                    items(uiState.upcomingBirthdays, key = { "b_${it.first.id}" }) { (contact, days) ->
                        val label = when (days) {
                            0 -> "Today"
                            1 -> "Tomorrow"
                            else -> "In $days days"
                        }
                        ContactListCard(
                            contact     = contact,
                            isDark      = isDark,
                            accentColor = PurplePrimary,
                            tag         = label,
                            onClick     = { onContactClick(contact.id) },
                            onLogCall   = { onLogCall(contact.id, contact.phoneNumber) },
                        )
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }

                // Empty state when nothing to show at all
                if (uiState.searchQuery.isBlank() &&
                    uiState.upcomingBirthdays.isEmpty() &&
                    uiState.followUpContacts.isEmpty() &&
                    uiState.starredContacts.isEmpty() &&
                    (uiState.recentInteractions.isEmpty() || !uiState.showRecentInteractions) &&
                    (uiState.recentContacts.isEmpty() && uiState.recentUnknownCalls.isEmpty() || !uiState.showRecentCalls)
                ) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "Nothing here yet -- star a contact or log an interaction to get started.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
