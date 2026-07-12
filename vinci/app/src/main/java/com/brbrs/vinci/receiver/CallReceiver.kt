package com.brbrs.vinci.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.brbrs.vinci.MainActivity
import com.brbrs.vinci.data.CallLogDao
import com.brbrs.vinci.util.normalizePhone
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Listens for PHONE_STATE broadcasts.
 *
 * - On RINGING: looks up the incoming number against past Vinci interactions
 *   (logged against a contact OR as an unknown-number log) and shows a
 *   "Caller history" notification immediately, while the phone is still ringing.
 * - On IDLE (call ended): shows a "Log this call?" notification, prioritizing
 *   the number captured at RINGING/OFFHOOK over a CallLog query (which can
 *   race with the system writing the new entry).
 *
 * State is persisted in SharedPreferences because BroadcastReceiver
 * instances are not reused between broadcasts -- instance variables are lost.
 */
@AndroidEntryPoint
class CallReceiver : BroadcastReceiver() {

    @Inject lateinit var callLogDao: CallLogDao

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.intent.action.PHONE_STATE") return

        val state       = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incomingNum = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
        val prefs       = prefs(context)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                prefs.edit()
                    .putBoolean(KEY_IN_PROGRESS, true)
                    .putString(KEY_NUMBER, incomingNum)
                    .putBoolean(KEY_IS_OUTGOING, false)
                    .apply()

                if (incomingNum.isNotBlank()) {
                    checkCallerHistory(context, incomingNum)
                }
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (!prefs.getBoolean(KEY_IN_PROGRESS, false)) {
                    // No ringing phase = outgoing call. Number not known yet here.
                    prefs.edit()
                        .putBoolean(KEY_IN_PROGRESS, true)
                        .putBoolean(KEY_IS_OUTGOING, true)
                        .putLong(KEY_START_TIME, System.currentTimeMillis())
                        .apply()
                } else {
                    prefs.edit()
                        .putLong(KEY_START_TIME, System.currentTimeMillis())
                        .apply()
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                val wasInProgress = prefs.getBoolean(KEY_IN_PROGRESS, false)
                val startTime     = prefs.getLong(KEY_START_TIME, 0L)
                val ringingNumber = prefs.getString(KEY_NUMBER, "") ?: ""
                val isOutgoing    = prefs.getBoolean(KEY_IS_OUTGOING, false)
                val duration      = if (startTime > 0) ((System.currentTimeMillis() - startTime) / 1000).toInt() else 0

                prefs.edit().clear().apply()

                if (!wasInProgress) return
                if (duration < 2) return

                // Number priority fix: trust the number captured at RINGING (incoming calls
                // always have it). Only fall back to the CallLog query for outgoing calls,
                // where EXTRA_INCOMING_NUMBER is never populated -- with a short delay to
                // let the system finish writing the new call log entry first.
                val resolvedNumber = if (ringingNumber.isNotBlank()) {
                    ringingNumber
                } else if (isOutgoing) {
                    Thread.sleep(1200)
                    resolveNumberFromCallLog(context) ?: ""
                } else {
                    ringingNumber
                }

                val contactId = resolveContactId(context, resolvedNumber)

                showLogCallNotification(
                    context    = context,
                    number     = resolvedNumber,
                    contactId  = contactId,
                    isOutgoing = isOutgoing,
                    duration   = duration,
                )
            }
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("vinci_call_state", Context.MODE_PRIVATE)

    // -- Caller history (on RINGING) -------------------------------------------

    /**
     * Looks up any past Vinci interactions for this number (logged against a
     * contact OR as an unknown-number log) and shows a notification with the
     * most recent note, while the phone is still ringing.
     */
    private fun checkCallerHistory(context: Context, incomingNumber: String) {
        val normalized = normalizePhone(incomingNumber)
        if (normalized.isBlank()) return

        // The BroadcastReceiver may be destroyed once onReceive returns --
        // use goAsync() to keep it alive briefly for the DB query.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val latestLog = callLogDao.getLatestLogForNumber(normalized)
                if (latestLog != null) {
                    showCallerHistoryNotification(context, incomingNumber, latestLog)
                }
            } catch (e: Exception) {
                // best-effort -- ignore failures
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showCallerHistoryNotification(
        context: Context,
        number: String,
        log: com.brbrs.vinci.data.CallLogEntity,
    ) {
        val channelId = "vinci_caller_history"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Caller history", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shows your past notes about an incoming caller"
            }
        )

        val dateStr  = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(log.callTimestamp))
        val noteText = log.notes.ifBlank { log.reason.ifBlank { log.outcome } }
        val summary  = if (noteText.isNotBlank()) "$dateStr: $noteText" else "Last contact: $dateStr"

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action", "view_history")
            if (log.contactId != null) putExtra("contact_id", log.contactId)
            putExtra("phone", number)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, (number + "_hist").hashCode(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Caller history: ${log.contactName.ifBlank { number }}")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(CALLER_HISTORY_NOTIFICATION_ID, notification)
    }

    // -- Post-call log notification ---------------------------------------------

    private fun resolveNumberFromCallLog(context: Context): String? {
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER),
                null, null,
                "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: SecurityException) { null }
    }

    private fun resolveContactId(context: Context, number: String): Long? {
        if (number.isBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup._ID),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        } catch (e: Exception) { null }
    }

    private fun showLogCallNotification(
        context: Context,
        number: String,
        contactId: Long?,
        isOutgoing: Boolean,
        duration: Int,
    ) {
        val channelId = "vinci_call_log"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(channelId, "Call log reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Prompts you to log a call after it ends"
            }
        )

        // Open intent -- deep link into log screen. contactId == null routes to
        // "no contact" mode (unknown-number interaction) in CallLogScreen.
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action", "log_call")
            if (contactId != null) putExtra("contact_id", contactId)
            putExtra("phone", number)
            putExtra("is_outgoing", isOutgoing)
            putExtra("duration", duration)
        }
        val requestCode = System.currentTimeMillis().toInt()
        val pendingIntent = PendingIntent.getActivity(
            context, requestCode, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val direction    = if (isOutgoing) "Outgoing" else "Incoming"
        val durationStr  = if (duration >= 60) "${duration / 60}m ${duration % 60}s" else "${duration}s"
        val contactLabel = contactId?.let { resolveContactName(context, it) } ?: number.ifBlank { "Unknown" }

        // Quick-add action -- inline text reply that saves a note without opening the app
        val remoteInput = RemoteInput.Builder(QuickNoteReceiver.KEY_NOTE)
            .setLabel("Add a quick note...")
            .build()

        val quickAddIntent = Intent(context, QuickNoteReceiver::class.java).apply {
            putExtra("phone", number)
            putExtra("contact_id", contactId ?: -1L)
            putExtra("contact_name", contactLabel)
            putExtra("is_outgoing", isOutgoing)
            putExtra("duration", duration)
            putExtra("notification_id", NOTIFICATION_ID)
        }
        val quickAddPendingIntent = PendingIntent.getBroadcast(
            context, requestCode + 1, quickAddIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val quickAddAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_edit, "Quick note", quickAddPendingIntent,
        ).addRemoteInput(remoteInput).build()

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Log call with $contactLabel?")
            .setContentText("$direction call \u00b7 $durationStr")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(quickAddAction)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun resolveContactName(context: Context, contactId: Long): String? {
        return try {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                "${ContactsContract.Contacts._ID} = ?",
                arrayOf(contactId.toString()),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) { null }
    }

    companion object {
        private const val KEY_IN_PROGRESS = "in_progress"
        private const val KEY_NUMBER      = "number"
        private const val KEY_IS_OUTGOING = "is_outgoing"
        private const val KEY_START_TIME  = "start_time"
        const val NOTIFICATION_ID = 1001
        const val CALLER_HISTORY_NOTIFICATION_ID = 1002
    }
}
