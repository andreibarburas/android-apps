package com.brbrs.blik.tasks

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object TasksOrgHelper {
    private const val TASKS_PACKAGE = "org.tasks"

    fun isInstalled(context: Context): Boolean =
        try {
            context.packageManager.getPackageInfo(TASKS_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) { false }

    fun createTask(context: Context, title: String, notes: String): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage(TASKS_PACKAGE)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, notes)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            try {
                context.startActivity(
                    context.packageManager.getLaunchIntentForPackage(TASKS_PACKAGE)
                        ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        ?: return false
                )
                true
            } catch (e2: Exception) { false }
        }
    }
}
