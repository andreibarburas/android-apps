package com.brbrs.vinci.network

import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import com.brbrs.vinci.data.ContactDao
import com.brbrs.vinci.data.ContactEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactDao: ContactDao,
    private val callLogDao: com.brbrs.vinci.data.CallLogDao,
) {    suspend fun syncFromDevice() = withContext(Dispatchers.IO) {
        val cr = context.contentResolver

        // 1. CardDAV UIDs from DAVx5.
        // DAVx5 stores the vCard's internal UID in SYNC1, but the actual filename
        // on the server in SOURCE_ID (full resource URL). These can differ.
        // We need the filename (from SOURCE_ID) to build correct CardDAV fetch URLs.
        val uidMap     = mutableMapOf<Long, String>()
        val davx5Ids   = mutableSetOf<Long>()
        cr.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(
                ContactsContract.RawContacts.CONTACT_ID,
                ContactsContract.RawContacts.SYNC1,
                ContactsContract.RawContacts.SOURCE_ID,
                ContactsContract.RawContacts.ACCOUNT_TYPE,
            ),
            "${ContactsContract.RawContacts.DELETED} = 0",
            null, null,
        )?.use { cursor ->
            val idCol       = cursor.getColumnIndex(ContactsContract.RawContacts.CONTACT_ID)
            val sync1Col    = cursor.getColumnIndex(ContactsContract.RawContacts.SYNC1)
            val sourceIdCol = cursor.getColumnIndex(ContactsContract.RawContacts.SOURCE_ID)
            val accountCol  = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
            while (cursor.moveToNext()) {
                val sync1       = cursor.getString(sync1Col) ?: ""
                val sourceId    = cursor.getString(sourceIdCol) ?: ""
                val accountType = cursor.getString(accountCol) ?: ""
                val contactId   = cursor.getLong(idCol)
                val isDavx5 = accountType.contains("bitfire", ignoreCase = true) ||
                              accountType.contains("davdroid", ignoreCase = true)

                if (isDavx5) {
                    davx5Ids.add(contactId)
                    // Extract filename from SOURCE_ID (the actual .vcf resource name on server)
                    // e.g. https://.../contacts-1/DA622FFC-031E-4D0E.vcf -> DA622FFC-031E-4D0E
                    val filenameUid = if (sourceId.contains(".vcf", ignoreCase = true)) {
                        sourceId.substringAfterLast("/").removeSuffix(".vcf").removeSuffix(".VCF")
                    } else ""
                    // Prefer filename UID; fall back to SYNC1 internal UID
                    val uid = filenameUid.ifBlank { sync1 }
                    if (uid.isNotBlank()) uidMap[contactId] = uid
                } else {
                    // Non-DAVx5 (WhatsApp, Google, etc.) — only use if no DAVx5 entry yet
                    val uid = sync1.ifBlank { sourceId }
                    if (uid.isNotBlank() && contactId !in davx5Ids) {
                        uidMap[contactId] = uid
                    }
                }
            }
            // Remove WhatsApp-style UIDs that may have overwritten DAVx5 ones
            davx5Ids.forEach { contactId ->
                val uid = uidMap[contactId] ?: return@forEach
                if ("@s.whatsapp.net" in uid || "@g.us" in uid || "@c.us" in uid) {
                    uidMap.remove(contactId)
                }
            }
        }

        // 2. Recent calls from system call log — map normalized number -> timestamp
        val recentCallMap = mutableMapOf<String, Long>()
        try {
            cr.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE),
                null, null,
                "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                val numCol  = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val dateCol = cursor.getColumnIndex(CallLog.Calls.DATE)
                while (cursor.moveToNext()) {
                    val num = normalizePhone(cursor.getString(numCol) ?: "")
                    val date = cursor.getLong(dateCol)
                    // Keep only the most recent call per number
                    if (num.isNotBlank() && !recentCallMap.containsKey(num)) {
                        recentCallMap[num] = date
                    }
                }
            }
        } catch (e: SecurityException) {
            // READ_CALL_LOG not yet granted — skip, will populate on next sync
        }

        // 3. Fetch all contacts
        val contacts = mutableListOf<ContactEntity>()
        cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_URI,
            ),
            null, null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
        )?.use { cursor ->
            val idCol    = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameCol  = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val photoCol = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)

            while (cursor.moveToNext()) {
                val id          = cursor.getLong(idCol)
                val displayName = cursor.getString(nameCol) ?: ""
                val photoUri    = cursor.getString(photoCol) ?: ""
                val cardavUid   = uidMap[id] ?: id.toString()

                val phones   = getAllPhones(cr, id)
                val emails   = getAllEmails(cr, id)
                val org      = getOrganization(cr, id)
                val links    = getSocialLinks(cr, id)
                val birthday = getBirthday(cr, id)
                val address  = getPrimaryAddress(cr, id)
                val notes    = getContactNotes(cr, id)

                val primaryPhone = try {
                    val arr = JSONArray(phones)
                    if (arr.length() > 0) arr.getJSONObject(0).optString("number", "") else ""
                } catch (e: Exception) { "" }

                val primaryEmail = try {
                    val arr = JSONArray(emails)
                    if (arr.length() > 0) arr.getJSONObject(0).optString("address", "") else ""
                } catch (e: Exception) { "" }

                // lastCallTimestamp: prefer logged Vinci calls, fall back to system call log
                val existing         = contactDao.getContactById(id)
                val normalizedPrimary = normalizePhone(primaryPhone)
                val systemCallTs     = recentCallMap[normalizedPrimary] ?: 0L
                val lastCallTs       = maxOf(existing?.lastCallTimestamp ?: 0L, systemCallTs)

                contacts.add(
                    ContactEntity(
                        id                = id,
                        cardavUid         = cardavUid,
                        displayName       = displayName,
                        phoneNumber       = primaryPhone,
                        allPhones         = phones,
                        email             = primaryEmail,
                        allEmails         = emails,
                        organization      = org.first,
                        jobTitle          = org.second,
                        photoUri          = photoUri,
                        notes             = notes,
                        birthday          = birthday,
                        address           = address,
                        lastCallTimestamp = lastCallTs,
                        followUpDue       = existing?.followUpDue ?: 0L,
                        isStarred         = existing?.isStarred ?: false,
                        socialLinks       = links,
                    )
                )
            }
        }

        if (contacts.isNotEmpty()) {
            contactDao.upsertAll(contacts)
            contactDao.deleteNotIn(contacts.map { it.id })

            // Re-link any unknown-number interaction logs to contacts whose
            // phone numbers now match -- e.g. a number that called and was
            // logged before being saved as a contact.
            for (contact in contacts) {
                val candidates = mutableSetOf<String>()
                val primary = normalizePhone(contact.phoneNumber)
                if (primary.isNotBlank()) candidates.add(primary)
                try {
                    val arr = JSONArray(contact.allPhones)
                    for (i in 0 until arr.length()) {
                        val num = normalizePhone(arr.getJSONObject(i).optString("number", ""))
                        if (num.isNotBlank()) candidates.add(num)
                    }
                } catch (e: Exception) { /* ignore malformed */ }

                for (normalized in candidates) {
                    callLogDao.relinkLogsToContact(normalized, contact.id)
                }
            }
        }
    }


    // Returns a deduplicated JSON array string of {type, number} objects
    private fun getAllPhones(cr: android.content.ContentResolver, contactId: Long): String {
        val arr = JSONArray()
        val seen = mutableSetOf<String>() // deduplicate by normalized number
        cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL,
                ContactsContract.CommonDataKinds.Phone.IS_PRIMARY,
            ),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = $contactId",
            null,
            "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC",
        )?.use { cursor ->
            val numCol   = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeCol  = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
            val labelCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)
            while (cursor.moveToNext()) {
                val number     = cursor.getString(numCol) ?: continue
                val normalized = normalizePhone(number)
                if (normalized in seen) continue
                seen.add(normalized)
                val type  = cursor.getInt(typeCol)
                val label = cursor.getString(labelCol) ?: ""
                arr.put(JSONObject().apply {
                    put("type", phoneTypeName(type, label))
                    put("number", number)
                })
            }
        }
        return arr.toString()
    }

    // Returns a deduplicated JSON array string of {type, address} objects
    private fun getAllEmails(cr: android.content.ContentResolver, contactId: Long): String {
        val arr = JSONArray()
        val seen = mutableSetOf<String>() // deduplicate by lowercase address
        cr.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.TYPE,
                ContactsContract.CommonDataKinds.Email.LABEL,
                ContactsContract.CommonDataKinds.Email.IS_PRIMARY,
            ),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = $contactId",
            null,
            "${ContactsContract.CommonDataKinds.Email.IS_PRIMARY} DESC",
        )?.use { cursor ->
            val addrCol  = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            val typeCol  = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE)
            val labelCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.LABEL)
            while (cursor.moveToNext()) {
                val address = cursor.getString(addrCol) ?: continue
                val key     = address.lowercase().trim()
                if (key in seen) continue
                seen.add(key)
                val type  = cursor.getInt(typeCol)
                val label = cursor.getString(labelCol) ?: ""
                arr.put(JSONObject().apply {
                    put("type", emailTypeName(type, label))
                    put("address", address)
                })
            }
        }
        return arr.toString()
    }

    private fun getOrganization(cr: android.content.ContentResolver, contactId: Long): Pair<String, String> {
        cr.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Organization.COMPANY,
                ContactsContract.CommonDataKinds.Organization.TITLE,
            ),
            "${ContactsContract.Data.CONTACT_ID} = $contactId AND ${ContactsContract.Data.MIMETYPE} = '${ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE}'",
            null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return Pair(cursor.getString(0) ?: "", cursor.getString(1) ?: "")
            }
        }
        return Pair("", "")
    }

    private fun getBirthday(cr: android.content.ContentResolver, contactId: Long): String {
        cr.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Event.START_DATE),
            "${ContactsContract.Data.CONTACT_ID} = $contactId AND ${ContactsContract.Data.MIMETYPE} = '${ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE}' AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ${ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY}",
            null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0) ?: ""
        }
        return ""
    }

    private fun getPrimaryAddress(cr: android.content.ContentResolver, contactId: Long): String {
        cr.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS),
            "${ContactsContract.Data.CONTACT_ID} = $contactId AND ${ContactsContract.Data.MIMETYPE} = '${ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE}'",
            null,
            "${ContactsContract.CommonDataKinds.StructuredPostal.IS_PRIMARY} DESC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0) ?: ""
        }
        return ""
    }

    private fun getContactNotes(cr: android.content.ContentResolver, contactId: Long): String {
        cr.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Note.NOTE),
            "${ContactsContract.Data.CONTACT_ID} = $contactId AND ${ContactsContract.Data.MIMETYPE} = '${ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE}'",
            null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0) ?: ""
        }
        return ""
    }

    private fun getSocialLinks(cr: android.content.ContentResolver, contactId: Long): String {
        val links = JSONArray()
        cr.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Website.URL,
                ContactsContract.CommonDataKinds.Website.LABEL,
            ),
            "${ContactsContract.Data.CONTACT_ID} = $contactId AND ${ContactsContract.Data.MIMETYPE} = '${ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE}'",
            null, null,
        )?.use { cursor ->
            val urlCol   = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Website.URL)
            val labelCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Website.LABEL)
            while (cursor.moveToNext()) {
                val url      = cursor.getString(urlCol) ?: continue
                val label    = cursor.getString(labelCol) ?: ""
                val platform = detectPlatform(url, label)
                links.put(JSONObject().apply {
                    put("platform", platform)
                    put("url", url)
                    // Store the label for "other" platform entries so the UI
                    // can show e.g. "Home" or "Work" instead of just "Other"
                    if (platform == "other" && label.isNotBlank()) put("label", label)
                })
            }
        }
        return links.toString()
    }

    private fun detectPlatform(url: String, label: String): String {
        val lower = url.lowercase()
        val lowerLabel = label.lowercase()
        return when {
            "linkedin.com"  in lower -> "linkedin"
            "twitter.com"   in lower ||
            "x.com"         in lower -> "x"
            "instagram.com" in lower -> "instagram"
            "github.com"    in lower -> "github"
            "facebook.com"  in lower -> "facebook"
            "mastodon."     in lower -> "mastodon"
            "youtube.com"   in lower -> "youtube"
            "pinterest.com" in lower -> "pinterest"
            "tumblr.com"    in lower -> "tumblr"
            "xing.com"      in lower -> "xing"
            "wechat"        in lower -> "wechat"
            else -> com.brbrs.vinci.ui.components.SOCIAL_PLATFORMS
                .firstOrNull { it.key == lowerLabel || it.label.lowercase() == lowerLabel }
                ?.key ?: "other"
        }
    }

    private fun phoneTypeName(type: Int, label: String): String = when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE  -> "Mobile"
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME    -> "Home"
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK    -> "Work"
        ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM  -> label.ifBlank { "Other" }
        else -> "Other"
    }

    private fun emailTypeName(type: Int, label: String): String = when (type) {
        ContactsContract.CommonDataKinds.Email.TYPE_HOME   -> "Home"
        ContactsContract.CommonDataKinds.Email.TYPE_WORK   -> "Work"
        ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM -> label.ifBlank { "Other" }
        else -> "Other"
    }

    /**
     * Returns true if the UID looks like a real Nextcloud/DAVx5 contact.
     * Filters out WhatsApp UIDs (@s.whatsapp.net, @g.us) and raw phone numbers.
     */
    private fun isNextcloudUid(uid: String): Boolean {
        if (uid.isBlank()) return false
        if (uid.contains("@s.whatsapp.net")) return false
        if (uid.contains("@g.us")) return false
        if (uid.all { it.isDigit() }) return false  // pure phone number used as UID
        // Accept standard UUIDs and alphanumeric IDs
        return uid.length > 4
    }

    private fun normalizePhone(number: String): String =
        number.replace(Regex("[^0-9+]"), "").trimStart('0')
}
