package com.brbrs.vinci.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.vinci.ui.theme.*
import com.brbrs.vinci.ui.viewmodels.FolderPickerViewModel

@Composable
fun FolderPickerScreen(
    onFolderConfirmed: () -> Unit,
    viewModel: FolderPickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.done) {
        if (uiState.done) onFolderConfirmed()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .glassCard(cornerRadius = 24.dp, bgAlpha = 0.08f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.CreateNewFolder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Choose your Vinci folder",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Vinci stores your call logs and contact notes as markdown files in your Nextcloud — so your data is always yours and survives phone changes. Choose where to keep them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            // Folder structure preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(cornerRadius = 16.dp, tint = MaterialTheme.colorScheme.primary),
                ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FolderLine(name = uiState.folderName.ifBlank { "Vinci" }, indent = 0, isRoot = true)
                    FolderLine(name = "Contacts/", indent = 1, isRoot = false)
                    FolderLine(name = "jan-de-vries-a1b2c3/", indent = 2, isRoot = false)
                    FolderLine(name = "calls/", indent = 3, isRoot = false)
                    FolderLine(name = "2026-06-06-0926.md", indent = 4, isRoot = false)
                }
            }

            OutlinedTextField(
                value = uiState.folderName,
                onValueChange = viewModel::onFolderNameChanged,
                label = { Text("Folder name") },
                placeholder = { Text("Vinci") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.primary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor     = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor   = MaterialTheme.colorScheme.onBackground,
                    cursorColor          = MaterialTheme.colorScheme.primary,
                    focusedLabelColor    = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor  = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
            )

            if (uiState.error != null) {
                Text(uiState.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = viewModel::confirm,
                enabled = uiState.folderName.isNotBlank() && !uiState.isCreating,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                if (uiState.isCreating) {
                    CircularProgressIndicator(color = NavyDeep, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Create folder and continue", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun FolderLine(name: String, indent: Int, isRoot: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = (indent * 16).dp),
    ) {
        Icon(
            if (name.endsWith("/") || isRoot) Icons.Outlined.Folder else Icons.Outlined.Folder,
            null,
            tint = if (isRoot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            color = if (isRoot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
