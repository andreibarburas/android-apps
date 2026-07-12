package com.brbrs.nota.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.brbrs.nota.tasks.TasksOrgHelper

/**
 * Renders nothing if Tasks.org integration is disabled or app not installed.
 *
 * [iconOnly] = true  → icon button for note cards and editor top bar
 * [iconOnly] = false → full-width outlined button (not currently used but available)
 */
@Composable
fun AddToTasksButton(
    noteTitle: String,
    noteContent: String,
    tasksEnabled: Boolean,
    iconOnly: Boolean = true,
) {
    val context = LocalContext.current
    if (!tasksEnabled) return
    if (!TasksOrgHelper.isInstalled(context)) return

    // Use the title as the task title; first 200 chars of content as notes
    val taskTitle = noteTitle.ifBlank { "Note from Nóta" }
    val taskNotes = noteContent
        .replace(Regex("""!\[[^\]]*\]\([^)]+\)"""), "") // strip image markdown
        .trim()
        .take(200)

    if (iconOnly) {
        IconButton(
            onClick = { TasksOrgHelper.createTask(context, taskTitle, taskNotes) }
        ) {
            Icon(
                Icons.Outlined.Alarm,
                contentDescription = "Add to Tasks.org",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        OutlinedButton(
            onClick = { TasksOrgHelper.createTask(context, taskTitle, taskNotes) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(Icons.Outlined.Alarm, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add to Tasks.org")
        }
    }
}
