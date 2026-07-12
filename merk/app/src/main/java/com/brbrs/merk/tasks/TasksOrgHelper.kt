package com.brbrs.merk.tasks

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

object TasksOrgHelper {

    private const val TASKS_PACKAGE = "org.tasks"

    fun isInstalled(context: Context): Boolean =
        try {
            context.packageManager.getPackageInfo(TASKS_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

    /**
     * Open Tasks.org with a new task prefilled.
     * Tasks.org supports the standard Android "send text" intent for task creation —
     * EXTRA_TEXT becomes the task title and EXTRA_SUBJECT becomes the notes/description.
     */
    fun createTask(
        context: Context,
        title: String,
        notes: String,
    ): Boolean {
        // Tasks.org registers as a handler for ACTION_SEND with text/plain,
        // using EXTRA_TEXT as the task title and EXTRA_SUBJECT as the note.
        val intent = Intent(Intent.ACTION_SEND).apply {
            type    = "text/plain"
            setPackage(TASKS_PACKAGE)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT,    notes)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            // Fallback: open Tasks.org main screen if the intent isn't handled
            try {
                context.startActivity(
                    context.packageManager.getLaunchIntentForPackage(TASKS_PACKAGE)
                        ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        ?: return false
                )
                true
            } catch (e2: Exception) {
                false
            }
        }
    }
}
