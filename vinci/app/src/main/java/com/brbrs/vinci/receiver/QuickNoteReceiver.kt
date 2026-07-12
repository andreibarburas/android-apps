package com.brbrs.vinci.receiver

import android.app.NotificationManager
import androidx.core.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import com.brbrs.vinci.data.CallLogDao
import com.brbrs.vinci.data.CallLogEntity
import com.brbrs.vinci.util.normalizePhone
import com.brbrs.vinci.util.stableLogId
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives the inline "Quick note" reply from the post-call notification and
 * saves a CallLogEntity directly -- no need to open the app.
 *
 * Works for both known contacts (contactId >= 0) and unknown numbers
 * (contactId == -1, stored as null with contactName as the user's label).
 */
@AndroidEntryPoint
class QuickNoteReceiver : BroadcastReceiver() {

    @Inject lateinit var callLogDao: CallLogDao

    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val note = remoteInput?.getCharSequence(KEY_NOTE)?.toString()?.trim() ?: ""

        val phone           = intent.getStringExtra("phone") ?: ""
        val contactIdExtra  = intent.getLongExtra("contact_id", -1L)
        val contactName     = intent.getStringExtra("contact_name") ?: phone
        val isOutgoing      = intent.getBooleanExtra("is_outgoing", false)
        val duration        = intent.getIntExtra("duration", 0)
        val notificationId  = intent.getIntExtra("notification_id", 1001)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (note.isNotBlank()) {
                    val contactId = if (contactIdExtra >= 0) contactIdExtra else null
                    val resolvedName = if (contactId != null) {
                        resolveContactName(context, contactId) ?: contactName
                    } else contactName

                    val now = System.currentTimeMillis()
                    callLogDao.insertLog(
                        CallLogEntity(
                            id              = stableLogId(now),
                            contactId       = contactId,
                            contactUid      = "",
                            contactName     = resolvedName,
                            phoneNumber     = phone,
                            normalizedPhone = normalizePhone(phone),
                            callTimestamp   = now,
                            durationSeconds = duration,
                            isOutgoing      = isOutgoing,
                            interactionType = "Call",
                            reason          = "",
                            outcome         = "",
                            notes           = note,
                            tags            = "",
                            followUpDays    = 0,
                            isSynced        = false,
                        )
                    )
                }
            } finally {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(notificationId)
                pendingResult.finish()
            }
        }
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
        const val KEY_NOTE = "quick_note"
    }
}
