package com.brbrs.vinci.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.vinci.auth.AuthRepository
import com.brbrs.vinci.network.RestoreResult
import com.brbrs.vinci.network.WebDavRepository
import com.brbrs.vinci.ui.theme.DisplayPreferencesRepository
import com.brbrs.vinci.tasks.TasksOrgHelper
import com.brbrs.vinci.tasks.TasksPreference
import com.brbrs.vinci.data.CallLogDao
import androidx.core.content.FileProvider
import android.content.Intent
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val vinciFolder: String = "Vinci",
    val appLockEnabled: Boolean = false,
    val tasksEnabled: Boolean = false,
    val tasksInstalled: Boolean = false,
    val loggedOut: Boolean = false,
    val isRestoring: Boolean = false,
    val isSyncing: Boolean = false,
    val restoreResult: String? = null,
    val isExporting: Boolean = false,
    val exportError: String? = null,
    val defaultCountryCode: String = "",
    val attachmentsKeepLocal: Boolean = false,
    val cachedAttachmentsSize: Long = 0,
    val cachedAttachmentsCount: Int = 0,
    val textSize: String = "default",
    val useCustomFont: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val tasksPref: TasksPreference,
    private val webDavRepository: WebDavRepository,
    private val displayPrefs: DisplayPreferencesRepository,
    private val callLogDao: CallLogDao,
    private val contactDao: com.brbrs.vinci.data.ContactDao,
    private val attachmentDao: com.brbrs.vinci.data.AttachmentDao,
) : ViewModel() {

    private val _loggedOut      = MutableStateFlow(false)
    private val _exportFile     = MutableStateFlow<File?>(null)
    private val _cacheInfo      = MutableStateFlow(0L to 0)
    val exportFile: StateFlow<File?> = _exportFile.asStateFlow()
    fun clearExportFile() { _exportFile.value = null }

    init { refreshCacheInfo() }

    private fun refreshCacheInfo() {
        viewModelScope.launch {
            val cached = attachmentDao.getCachedAttachments()
            val totalSize = cached.sumOf { it.sizeBytes }
            _cacheInfo.value = totalSize to cached.size
        }
    }

    /** Deletes all locally-cached attachment files (Nextcloud copies are untouched). */
    fun clearAttachmentCache() {
        viewModelScope.launch {
            val cached = attachmentDao.getCachedAttachments()
            cached.forEach { att ->
                if (att.localPath.isNotBlank()) runCatching { File(att.localPath).delete() }
                attachmentDao.clearLocalCache(att.id)
            }
            refreshCacheInfo()
        }
    }

    fun setAttachmentsKeepLocal(enabled: Boolean) {
        viewModelScope.launch { displayPrefs.setAttachmentsKeepLocal(enabled) }
    }

    // Bundle transient operation state into one flow so combine() stays under the 5-flow limit
    private data class OpState(
        val isRestoring: Boolean = false,
        val isSyncing: Boolean = false,
        val restoreResult: String? = null,
        val isExporting: Boolean = false,
        val exportError: String? = null,
    )
    private val _opState = MutableStateFlow(OpState())

    val uiState: StateFlow<SettingsUiState> = combine(
        authRepository.session,
        authRepository.appLockEnabled,
        tasksPref.enabled,
        combine(_loggedOut, _cacheInfo) { loggedOut, cache -> loggedOut to cache },
        combine(displayPrefs.preferences, _opState) { display, op -> display to op },
    ) { session, appLock, tasks, loggedOutAndCache, displayAndOp ->
        val (loggedOut, cache) = loggedOutAndCache
        val (display, op) = displayAndOp
        SettingsUiState(
            serverUrl      = session?.serverUrl ?: "",
            username       = session?.username  ?: "",
            vinciFolder    = session?.vinciFolder ?: "Vinci",
            appLockEnabled = appLock,
            tasksEnabled   = tasks,
            tasksInstalled = TasksOrgHelper.isInstalled(context),
            loggedOut      = loggedOut,
            isRestoring    = op.isRestoring,
            isSyncing      = op.isSyncing,
            restoreResult  = op.restoreResult,
            isExporting    = op.isExporting,
            exportError    = op.exportError,
            defaultCountryCode      = display.defaultCountryCode,
            attachmentsKeepLocal    = display.attachmentsKeepLocal,
            cachedAttachmentsSize   = cache.first,
            cachedAttachmentsCount  = cache.second,
            textSize                = display.textSize,
            useCustomFont           = display.useCustomFont,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setTextSize(size: String) {
        viewModelScope.launch { displayPrefs.setTextSize(size) }
    }

    fun setUseCustomFont(enabled: Boolean) {
        viewModelScope.launch { displayPrefs.setUseCustomFont(enabled) }
    }

    fun setDefaultCountryCode(code: String) {
        viewModelScope.launch { displayPrefs.setDefaultCountryCode(code) }
    }

    fun setAppLock(enabled: Boolean) {
        viewModelScope.launch { authRepository.setAppLockEnabled(enabled) }
    }

    fun setVinciFolder(folder: String) {
        viewModelScope.launch { authRepository.saveVinciFolder(folder.trim().ifBlank { "Vinci" }) }
    }

    fun toggleTasks(enabled: Boolean) {
        viewModelScope.launch { tasksPref.setEnabled(enabled) }
    }

    fun syncStarredNow() {
        viewModelScope.launch {
            _opState.update { it.copy(isSyncing = true) }
            _opState.update { it.copy(restoreResult = "Syncing starred contacts…") }
            val starred = contactDao.getStarredContactsOnce()
            var synced = 0
            starred.forEach { contact ->
                runCatching {
                    webDavRepository.writeIndexMd(
                        contactUid  = contact.cardavUid,
                        contactName = contact.displayName,
                        isStarred   = true,
                        followUpDue = contact.followUpDue,
                    )
                    synced++
                }
            }
            _opState.update { it.copy(isSyncing = false) }
            _opState.update { it.copy(restoreResult = "Synced $synced starred contacts to Nextcloud.") }
        }
    }

    fun restoreNow() {
        viewModelScope.launch {
            _opState.update { it.copy(isRestoring = true) }
            _opState.update { it.copy(restoreResult = "Starting restore…") }
            val result = webDavRepository.restoreFromNextcloud()
            _opState.update { it.copy(isRestoring = false) }
            _opState.update { it.copy(restoreResult = if (result.error != null)
                "Restore failed: ${result.error}"
            else
                "Restored ${result.contactsRestored} contacts and ${result.logsImported} interactions."
            ) }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.clearSession()
            _loggedOut.value = true
        }
    }

    // -- Export / backup -------------------------------------------------------

    fun exportAsMarkdown() {
        viewModelScope.launch {
            _opState.update { it.copy(isExporting = true) }
            _opState.update { it.copy(exportError = null) }
            try {
                val logs = callLogDao.getAllLogs().first()
                val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val sb = StringBuilder()
                sb.append("# Vinci interactions export\n\n")
                sb.append("Exported ${dateFmt.format(Date())}  -- ${logs.size} interactions\n\n")

                val grouped = logs.groupBy { SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(it.callTimestamp)) }
                grouped.forEach { (month, monthLogs) ->
                    sb.append("## $month\n\n")
                    monthLogs.forEach { log ->
                        sb.append("### ${log.contactName}\n")
                        sb.append("- Date: ${dateFmt.format(Date(log.callTimestamp))}\n")
                        sb.append("- Type: ${log.interactionType}\n")
                        if (log.reason.isNotBlank())  sb.append("- Reason: ${log.reason}\n")
                        if (log.outcome.isNotBlank()) sb.append("- Outcome: ${log.outcome}\n")
                        if (log.phoneNumber.isNotBlank()) sb.append("- Phone: ${log.phoneNumber}\n")
                        val tags = parseTagsForExport(log.tags)
                        if (tags.isNotEmpty()) sb.append("- Tags: ${tags.joinToString(", ")}\n")
                        if (log.notes.isNotBlank()) {
                            sb.append("\n${log.notes}\n")
                        }
                        sb.append("\n")
                    }
                }

                val file = writeExportFile("vinci-interactions.md", sb.toString())
                _exportFile.value = file
            } catch (e: Exception) {
                _opState.update { it.copy(exportError = "Export failed: ${e.message}") }
            } finally {
                _opState.update { it.copy(isExporting = false) }
            }
        }
    }

    fun exportAsCsv() {
        viewModelScope.launch {
            _opState.update { it.copy(isExporting = true) }
            _opState.update { it.copy(exportError = null) }
            try {
                val logs = callLogDao.getAllLogs().first()
                val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val sb = StringBuilder()
                sb.append("date,contact,phone,type,reason,outcome,tags,notes\n")
                logs.forEach { log ->
                    val tags = parseTagsForExport(log.tags).joinToString("; ")
                    sb.append(csvEscape(dateFmt.format(Date(log.callTimestamp)))).append(",")
                    sb.append(csvEscape(log.contactName)).append(",")
                    sb.append(csvEscape(log.phoneNumber)).append(",")
                    sb.append(csvEscape(log.interactionType)).append(",")
                    sb.append(csvEscape(log.reason)).append(",")
                    sb.append(csvEscape(log.outcome)).append(",")
                    sb.append(csvEscape(tags)).append(",")
                    sb.append(csvEscape(log.notes)).append("\n")
                }

                val file = writeExportFile("vinci-interactions.csv", sb.toString())
                _exportFile.value = file
            } catch (e: Exception) {
                _opState.update { it.copy(exportError = "Export failed: ${e.message}") }
            } finally {
                _opState.update { it.copy(isExporting = false) }
            }
        }
    }

    private fun csvEscape(value: String): String {
        val needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n")
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    private fun parseTagsForExport(json: String): List<String> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) { emptyList() }
    }

    private fun writeExportFile(name: String, content: String): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, name)
        file.writeText(content)
        return file
    }

    /** Returns a sharable content:// URI for the given export file via FileProvider. */
    fun shareIntentFor(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "com.brbrs.vinci.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = if (file.extension == "csv") "text/csv" else "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
