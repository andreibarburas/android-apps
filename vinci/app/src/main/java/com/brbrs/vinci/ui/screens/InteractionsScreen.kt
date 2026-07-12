package com.brbrs.vinci.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.vinci.ui.components.RecentInteractionCard
import com.brbrs.vinci.ui.theme.*
import com.brbrs.vinci.ui.viewmodels.InteractionsViewModel

@Composable
fun InteractionsScreen(
    onEditInteraction: (Long) -> Unit,
    viewModel: InteractionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = LocalIsDark.current

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
                Text("Interactions", style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground)
                if (uiState.total > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(CyanPrimary.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text("${uiState.total}", style = MaterialTheme.typography.labelMedium, color = CyanPrimary)
                    }
                }
            }

            if (uiState.groups.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No interactions logged yet.\nTap \"Log\" on any contact to get started.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
                    uiState.groups.forEach { (month, logs) ->
                        item(key = "header_$month") {
                            SectionHeaderMonth(text = month, badge = logs.size)
                        }
                        items(logs, key = { it.id }) { log ->
                            RecentInteractionCard(log = log, isDark = isDark, showDate = true) {
                                onEditInteraction(log.id)
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderMonth(text: String, badge: Int) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = CyanPrimary.copy(alpha = 0.7f),
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .background(CyanPrimary.copy(alpha = 0.15f))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(badge.toString(), style = MaterialTheme.typography.labelSmall, color = CyanPrimary)
        }
    }
}
