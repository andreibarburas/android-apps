package com.brbrs.vinci.ui.viewmodels

import android.content.ContentProviderOperation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import java.io.ByteArrayOutputStream
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.vinci.data.ContactDao
import com.brbrs.vinci.data.ContactEntity
import com.brbrs.vinci.network.CardDavRepository
import com.brbrs.vinci.network.ContactsRepository
import com.brbrs.vinci.ui.components.SOCIAL_PLATFORMS
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class PhoneEntry(val type: String, val number: String)
data class EmailEntry(val type: String, val address: String)
data class SocialEntry(val platform: String, val url: String)
data class CustomField(val label: String, val value: String)

data class EditContactUiState(
    val contact: ContactEntity? = null,
    val isNewContact: Boolean = false,
    val photoUri: String = "",
    val displayName: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val organization: String = "",
    val jobTitle: String = "",
    val phones: List<PhoneEntry> = emptyList(),
    val emails: List<EmailEntry> = emptyList(),
    val address: String = "",
    val birthday: String = "",
    val notes: String = "",
    val socialLinks: List<SocialEntry> = emptyList(),
    val customFields: List<CustomField> = emptyList(),
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

val PHONE_TYPES  = listOf("Mobile", "Home", "Work", "Other")
val EMAIL_TYPES  = listOf("Home", "Work", "Other")

@HiltViewModel
class EditContactViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val contactDao: ContactDao,
    private val contactsRepository: ContactsRepository,
    private val cardDavRepository: CardDavRepository,
) : ViewModel() {

    private val contactId: Long = checkNotNull(savedStateHandle["contactId"])
    private val _uiState = MutableStateFlow(EditContactUiState())
    val uiState: StateFlow<EditContactUiState> = _uiState.asStateFlow()

    // Raw contact ID needed for ContactsContract writes
    private var rawContactId: Long = -1L

    // Pending photo bytes selected by the user but not yet saved
    private var pendingPhotoBytes: ByteArray? = null

    init {
        viewModelScope.launch {
            if (contactId <= 0L) {
                // Create mode -- start with a blank form
                _uiState.update {
                    it.copy(
                        isNewContact = true,
                        phones       = listOf(PhoneEntry("Mobile", "")),
                        emails       = listOf(EmailEntry("Home", "")),
                    )
                }
            } else {
                loadContact()
            }
        }
    }

    private suspend fun loadContact() = withContext(Dispatchers.IO) {
        val contact = contactDao.getContactById(contactId) ?: return@withContext

        // Parse stored JSON arrays
        val phones  = parsePhones(contact.allPhones)
        val emails  = parseEmails(contact.allEmails)
        val socials = parseSocials(contact.socialLinks)

        // Parse display name into first/last
        val nameParts = contact.displayName.trim().split(" ", limit = 2)
        val firstName = nameParts.getOrElse(0) { "" }
        val lastName  = nameParts.getOrElse(1) { "" }

        // Find raw contact ID from ContactsContract
        rawContactId = findRawContactId(contactId)

        _uiState.update {
            it.copy(
                contact      = contact,
                displayName  = contact.displayName,
                firstName    = firstName,
                lastName     = lastName,
                organization = contact.organization,
                jobTitle     = contact.jobTitle,
                phones       = phones,
                emails       = emails,
                address      = contact.address,
                birthday     = contact.birthday,
                notes        = contact.notes,
                socialLinks  = socials,
                customFields = emptyList(),
            )
        }
    }

    // -- Field update functions -----------------------------------------------

    fun onFirstNameChanged(v: String)    { _uiState.update { it.copy(firstName = v) } }
    fun onLastNameChanged(v: String)     { _uiState.update { it.copy(lastName = v) } }
    fun onOrganizationChanged(v: String) { _uiState.update { it.copy(organization = v) } }
    fun onJobTitleChanged(v: String)     { _uiState.update { it.copy(jobTitle = v) } }
    fun onAddressChanged(v: String)      { _uiState.update { it.copy(address = v) } }
    fun onBirthdayChanged(v: String)     { _uiState.update { it.copy(birthday = v) } }
    fun onNotesChanged(v: String)        { _uiState.update { it.copy(notes = v) } }

    /**
     * Called when the user picks a new photo. Decodes and downsizes the image,
     * stores it for writing on save(), and updates the preview.
     */
    fun setPhoto(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val input = context.contentResolver.openInputStream(uri) ?: return@runCatching
                    val original = BitmapFactory.decodeStream(input)
                    input.close()
                    // Downscale to a reasonable max dimension for contact photos
                    val maxDim = 512
                    val scale = minOf(1f, maxDim.toFloat() / maxOf(original.width, original.height))
                    val scaled = if (scale < 1f) {
                        Bitmap.createScaledBitmap(
                            original,
                            (original.width * scale).toInt().coerceAtLeast(1),
                            (original.height * scale).toInt().coerceAtLeast(1),
                            true,
                        )
                    } else original
                    val out = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    pendingPhotoBytes = out.toByteArray()
                }
            }
            _uiState.update { it.copy(photoUri = uri.toString()) }
        }
    }

    fun addPhone()  { _uiState.update { it.copy(phones = it.phones + PhoneEntry("Mobile", "")) } }
    fun removePhone(index: Int) { _uiState.update { it.copy(phones = it.phones.toMutableList().also { l -> l.removeAt(index) }) } }
    fun updatePhone(index: Int, entry: PhoneEntry) {
        _uiState.update { it.copy(phones = it.phones.toMutableList().also { l -> l[index] = entry }) }
    }

    fun addEmail()  { _uiState.update { it.copy(emails = it.emails + EmailEntry("Home", "")) } }
    fun removeEmail(index: Int) { _uiState.update { it.copy(emails = it.emails.toMutableList().also { l -> l.removeAt(index) }) } }
    fun updateEmail(index: Int, entry: EmailEntry) {
        _uiState.update { it.copy(emails = it.emails.toMutableList().also { l -> l[index] = entry }) }
    }

    fun addSocial()  { _uiState.update { it.copy(socialLinks = it.socialLinks + SocialEntry("other", "")) } }
    fun removeSocial(index: Int) { _uiState.update { it.copy(socialLinks = it.socialLinks.toMutableList().also { l -> l.removeAt(index) }) } }
    fun updateSocial(index: Int, entry: SocialEntry) {
        _uiState.update { it.copy(socialLinks = it.socialLinks.toMutableList().also { l -> l[index] = entry }) }
    }

    fun addCustomField() { _uiState.update { it.copy(customFields = it.customFields + CustomField("", "")) } }
    fun removeCustomField(index: Int) { _uiState.update { it.copy(customFields = it.customFields.toMutableList().also { l -> l.removeAt(index) }) } }
    fun updateCustomField(index: Int, field: CustomField) {
        _uiState.update { it.copy(customFields = it.customFields.toMutableList().also { l -> l[index] = field }) }
    }

    // -- Save -----------------------------------------------------------------

    fun save() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val state = _uiState.value

            try {
                withContext(Dispatchers.IO) {
                    if (state.isNewContact) {
                        createNewContact(state)
                    } else {
                        writeToContactsProvider(state)
                        // Write social links back to Nextcloud via CardDAV
                        // (DAVx5 does not round-trip X-SOCIALPROFILE through ContactsContract)
                        val contact = state.contact
                        if (contact != null && contact.cardavUid.isNotBlank()) {
                            val socialJson = JSONArray(
                                state.socialLinks.filter { it.url.isNotBlank() }.map { s ->
                                    JSONObject().apply {
                                        put("platform", s.platform)
                                        put("url", s.url)
                                    }
                                }
                            ).toString()
                            cardDavRepository.writeSocialLinks(contact.cardavUid, socialJson)
                        }
                    }
                }
                // Re-sync Room cache from device
                contactsRepository.syncFromDevice()
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "Save failed: ${e.message}") }
            }
        }
    }

    /**
     * Creates a brand new contact via ContentProviderOperation back-references.
     * Uses the same account (DAVx5 / Nextcloud) as any existing synced contact,
     * so the new contact syncs to Nextcloud automatically.
     */
    private fun createNewContact(state: EditContactUiState) {
        if (state.firstName.isBlank() && state.lastName.isBlank()) {
            throw Exception("Please enter a name")
        }

        val account = findSyncAccount()
        val ops = ArrayList<ContentProviderOperation>()
        val rawContactIndex = ops.size

        val rawContactOp = ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
        if (account != null) {
            rawContactOp
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, account.first)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, account.second)
        }
        ops.add(rawContactOp.build())

        val displayName = "${state.firstName.trim()} ${state.lastName.trim()}".trim()

        // Structured name
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
            .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, state.firstName.trim())
            .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, state.lastName.trim())
            .build())

        // Organization
        if (state.organization.isNotBlank() || state.jobTitle.isNotBlank()) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, state.organization)
                .withValue(ContactsContract.CommonDataKinds.Organization.TITLE, state.jobTitle)
                .build())
        }

        // Phones
        state.phones.filter { it.number.isNotBlank() }.forEachIndexed { i, phone ->
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone.number)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, phoneTypeInt(phone.type))
                .withValue(ContactsContract.CommonDataKinds.Phone.IS_PRIMARY, if (i == 0) 1 else 0)
                .build())
        }

        // Emails
        state.emails.filter { it.address.isNotBlank() }.forEachIndexed { i, email ->
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email.address)
                .withValue(ContactsContract.CommonDataKinds.Email.TYPE, emailTypeInt(email.type))
                .withValue(ContactsContract.CommonDataKinds.Email.IS_PRIMARY, if (i == 0) 1 else 0)
                .build())
        }

        // Address
        if (state.address.isNotBlank()) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, state.address)
                .build())
        }

        // Birthday
        if (state.birthday.isNotBlank()) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Event.START_DATE, state.birthday)
                .withValue(ContactsContract.CommonDataKinds.Event.TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY)
                .build())
        }

        // Notes
        if (state.notes.isNotBlank()) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Note.NOTE, state.notes)
                .build())
        }

        // Photo
        pendingPhotoBytes?.let { bytes ->
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, bytes)
                .build())
        }

        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    /**
     * Finds an existing DAVx5/Nextcloud sync account from any currently-synced
     * raw contact, so new contacts are created under the same account.
     * Returns Pair(accountType, accountName) or null if none found (local contact).
     */
    private fun findSyncAccount(): Pair<String, String>? {
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.ACCOUNT_TYPE, ContactsContract.RawContacts.ACCOUNT_NAME),
            "${ContactsContract.RawContacts.DELETED} = 0 AND ${ContactsContract.RawContacts.ACCOUNT_TYPE} IS NOT NULL",
            null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val type = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                if (type.contains("bitfire", ignoreCase = true) || type.contains("davdroid", ignoreCase = true)) {
                    return Pair(type, name)
                }
            }
        }
        return null
    }

    // -- ContactsContract write -----------------------------------------------

    private fun writeToContactsProvider(state: EditContactUiState) {
        val ops = ArrayList<ContentProviderOperation>()
        val displayName = "${state.firstName.trim()} ${state.lastName.trim()}".trim()
        val rawId = rawContactId

        if (rawId < 0) throw Exception("Raw contact not found")

        // Structured name
        val nameRow = findDataRow(rawId, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        if (nameRow >= 0) {
            ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection("${ContactsContract.Data._ID} = ?", arrayOf(nameRow.toString()))
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, state.firstName.trim())
                .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, state.lastName.trim())
                .build())
        } else {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, state.firstName.trim())
                .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, state.lastName.trim())
                .build())
        }

        // Organization
        deleteDataRows(ops, rawId, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
        if (state.organization.isNotBlank() || state.jobTitle.isNotBlank()) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, state.organization)
                .withValue(ContactsContract.CommonDataKinds.Organization.TITLE, state.jobTitle)
                .build())
        }

        // Phones — delete all existing, re-insert
        deleteDataRows(ops, rawId, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
        state.phones.filter { it.number.isNotBlank() }.forEachIndexed { i, phone ->
            val type = phoneTypeInt(phone.type)
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone.number)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, type)
                .withValue(ContactsContract.CommonDataKinds.Phone.IS_PRIMARY, if (i == 0) 1 else 0)
                .build())
        }

        // Emails — delete all existing, re-insert
        deleteDataRows(ops, rawId, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
        state.emails.filter { it.address.isNotBlank() }.forEachIndexed { i, email ->
            val type = emailTypeInt(email.type)
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email.address)
                .withValue(ContactsContract.CommonDataKinds.Email.TYPE, type)
                .withValue(ContactsContract.CommonDataKinds.Email.IS_PRIMARY, if (i == 0) 1 else 0)
                .build())
        }

        // Address
        deleteDataRows(ops, rawId, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
        if (state.address.isNotBlank()) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, state.address)
                .build())
        }

        // Birthday
        deleteDataRows(ops, rawId, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)
        if (state.birthday.isNotBlank()) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Event.START_DATE, state.birthday)
                .withValue(ContactsContract.CommonDataKinds.Event.TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY)
                .build())
        }

        // Notes
        deleteDataRows(ops, rawId, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
        if (state.notes.isNotBlank()) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Note.NOTE, state.notes)
                .build())
        }

        // Social links / websites
        deleteDataRows(ops, rawId, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE)
        state.socialLinks.filter { it.url.isNotBlank() }.forEach { social ->
            val platform = SOCIAL_PLATFORMS.firstOrNull { it.key == social.platform }
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Website.URL, social.url)
                .withValue(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_CUSTOM)
                .withValue(ContactsContract.CommonDataKinds.Website.LABEL, platform?.label ?: "Other")
                .build())
        }

        // Custom fields — stored as X-VINCI- extension fields via Im mimetype with custom protocol
        deleteCustomFields(ops, rawId)
        state.customFields.filter { it.label.isNotBlank() && it.value.isNotBlank() }.forEach { field ->
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Im.DATA, field.value)
                .withValue(ContactsContract.CommonDataKinds.Im.TYPE, ContactsContract.CommonDataKinds.Im.TYPE_CUSTOM)
                .withValue(ContactsContract.CommonDataKinds.Im.LABEL, "X-VINCI-${field.label}")
                .withValue(ContactsContract.CommonDataKinds.Im.PROTOCOL, ContactsContract.CommonDataKinds.Im.PROTOCOL_CUSTOM)
                .withValue(ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL, "Vinci")
                .build())
        }

        // Photo -- only touch if the user picked a new one
        pendingPhotoBytes?.let { bytes ->
            deleteDataRows(ops, rawId, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, bytes)
                .build())
        }

        // Mark raw contact as dirty so DAVx5 picks it up
        ops.add(ContentProviderOperation.newUpdate(ContactsContract.RawContacts.CONTENT_URI)
            .withSelection("${ContactsContract.RawContacts._ID} = ?", arrayOf(rawId.toString()))
            .withValue(ContactsContract.RawContacts.DIRTY, 1)
            .build())

        if (ops.isNotEmpty()) {
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        }
    }

    // -- ContactsContract helpers ---------------------------------------------

    private fun findRawContactId(contactId: Long): Long {
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ? AND ${ContactsContract.RawContacts.DELETED} = 0",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return -1L
    }

    private fun findDataRow(rawContactId: Long, mimeType: String): Long {
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID),
            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(rawContactId.toString(), mimeType),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return -1L
    }

    private fun deleteDataRows(ops: ArrayList<ContentProviderOperation>, rawContactId: Long, mimeType: String) {
        ops.add(ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
            .withSelection(
                "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(rawContactId.toString(), mimeType),
            )
            .build())
    }

    private fun deleteCustomFields(ops: ArrayList<ContentProviderOperation>, rawContactId: Long) {
        ops.add(ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
            .withSelection(
                "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Im.LABEL} LIKE ?",
                arrayOf(rawContactId.toString(), ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE, "X-VINCI-%"),
            )
            .build())
    }

    // -- JSON parsers ---------------------------------------------------------

    private fun parsePhones(json: String): List<PhoneEntry> {
        if (json.isBlank() || json == "[]") return listOf(PhoneEntry("Mobile", ""))
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                PhoneEntry(obj.optString("type", "Mobile"), obj.optString("number", ""))
            }
        } catch (e: Exception) { listOf(PhoneEntry("Mobile", "")) }
    }

    private fun parseEmails(json: String): List<EmailEntry> {
        if (json.isBlank() || json == "[]") return listOf(EmailEntry("Home", ""))
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                EmailEntry(obj.optString("type", "Home"), obj.optString("address", ""))
            }
        } catch (e: Exception) { listOf(EmailEntry("Home", "")) }
    }

    private fun parseSocials(json: String): List<SocialEntry> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val url      = obj.optString("url", "")
                val platform = obj.optString("platform", "other").lowercase()
                SocialEntry(platform, url)
            }
        } catch (e: Exception) { emptyList() }
    }

    // -- Type converters ------------------------------------------------------

    private fun phoneTypeInt(type: String) = when (type) {
        "Mobile" -> ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
        "Home"   -> ContactsContract.CommonDataKinds.Phone.TYPE_HOME
        "Work"   -> ContactsContract.CommonDataKinds.Phone.TYPE_WORK
        else     -> ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
    }

    private fun emailTypeInt(type: String) = when (type) {
        "Home"   -> ContactsContract.CommonDataKinds.Email.TYPE_HOME
        "Work"   -> ContactsContract.CommonDataKinds.Email.TYPE_WORK
        else     -> ContactsContract.CommonDataKinds.Email.TYPE_OTHER
    }
}
