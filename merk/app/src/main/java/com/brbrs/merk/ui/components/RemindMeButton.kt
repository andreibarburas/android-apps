package com.brbrs.merk.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.brbrs.merk.tasks.TasksOrgHelper
import com.brbrs.merk.ui.theme.*

/**
 * One-tap button that opens Tasks.org with the bookmark prefilled as a new task.
 * Renders nothing if Tasks.org integration is disabled or app is not installed.
 *
 * [iconOnly] = true  → alarm icon button (list cards, detail top bar)
 * [iconOnly] = false → full-width outlined button (edit screen)
 */
@Composable
fun RemindMeButton(
    bookmarkTitle: String,
    bookmarkUrl: String,
    tasksEnabled: Boolean,
    iconOnly: Boolean = true,
) {
    val context = LocalContext.current
    if (!tasksEnabled) return
    if (!TasksOrgHelper.isInstalled(context)) return

    val taskTitle = "Read: ${bookmarkTitle.ifBlank { bookmarkUrl }}"
    val taskNotes = bookmarkUrl

    if (iconOnly) {
        IconButton(
            onClick = { TasksOrgHelper.createTask(context, taskTitle, taskNotes) }
        ) {
            Icon(Icons.Outlined.Alarm, contentDescription = "Add to Tasks.org", tint = SlateText)
        }
    } else {
        OutlinedButton(
            onClick  = { TasksOrgHelper.createTask(context, taskTitle, taskNotes) },
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = CyanPrimary),
        ) {
            Icon(Icons.Outlined.Alarm, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add to Tasks.org")
        }
    }
}
