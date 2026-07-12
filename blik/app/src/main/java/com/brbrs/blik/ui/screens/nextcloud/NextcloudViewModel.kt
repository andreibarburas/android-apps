package com.brbrs.blik.ui.screens.nextcloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.blik.auth.AuthManager
import com.brbrs.blik.data.local.ScreenshotDao
import com.brbrs.blik.data.local.UploadStatus
import com.brbrs.blik.data.repository.ScreenshotRepository
import com.brbrs.blik.data.repository.SettingsRepository
import com.brbrs.blik.network.WebDavClient
import com.brbrs.blik.ui.theme.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A file that exists on Nextcloud, enriched with local availability info. */
data class RemoteFile(
    val fileName: String,
    val remotePath: String,           // full path e.g. /Screenshots/travel/IMG_001.png
    val remoteFolder: String,         // category folder e.g. /Screenshots/travel
    val isAvailableLocally: Boolean,  // true if we have a local record with UPLOADED status
    val localPath: String?,           // content URI if available locally
    val webDavUrl: String = "",       // full https:// URL for authenticated remote loading
    val authHeader: String = "",      // "Basic ..." header value for Coil
)

data class NextcloudUiState(
    val files: List<RemoteFile> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isDark: Boolean = true,
    val selectedPaths: Set<String> = emptySet(),  // remotePaths
    val isLoggedIn: Boolean = false,
    val showDeleteDialog: Boolean = false,
) {
    val isSelecting: Boolean get() = selectedPaths.isNotEmpty()
}

@HiltViewModel
class NextcloudViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val webDavClient: WebDavClient,
    private val dao: ScreenshotDao,
    private val settings: SettingsRepository,
    private val repo: ScreenshotRepository,
    private val themeRepo: ThemeRepository,
) : ViewModel() {

    private val _files         = MutableStateFlow<List<RemoteFile>>(emptyList())
    private val _isLoading     = MutableStateFlow(false)
    private val _error         = MutableStateFlow<String?>(null)
    private val _selectedPaths = MutableStateFlow<Set<String>>(emptySet())
    private val _showDelete    = MutableStateFlow(false)

    val uiState: StateFlow<NextcloudUiState> = combine(
        _files, _isLoading, _error, themeRepo.isDark,
    ) { files, loading, error, dark ->
        NextcloudUiState(files = files, isLoading = loading, error = error, isDark = dark)
    }.combine(
        combine(authManager.credentials, _selectedPaths, _showDelete) { creds, sel, del ->
            Triple(creds != null, sel, del)
        }
    ) { state, (loggedIn, sel, del) ->
        state.copy(isLoggedIn = loggedIn, selectedPaths = sel, showDeleteDialog = del)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NextcloudUiState())

    /** Fetch all files from Nextcloud and cross-reference with local DB. */
    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value     = null
            try {
                val creds = authManager.credentials.firstOrNull()
                    ?: throw Exception("Not connected to Nextcloud")
                val baseFolder = settings.remoteFolder.firstOrNull() ?: "/Screenshots"

                // Get all local uploaded records for cross-referencing
                val localUploaded = dao.observeAll().firstOrNull()
                    ?.filter { it.uploadStatus == UploadStatus.UPLOADED }
                    ?: emptyList()
                val localByRemotePath = localUploaded.associateBy { it.remotePath }

                // Collect remote files — root folder + one level of subfolders (categories)
                val remoteFiles = mutableListOf<RemoteFile>()

                // Scan root
                remoteFiles += scanFolder(creds, baseFolder, baseFolder, localByRemotePath)

                // Scan category subfolders
                val subFolders = try {
                    webDavClient.listFolders(creds, baseFolder)
                } catch (e: Exception) { emptyList() }

                for (sub in subFolders) {
                    val subPath = "${baseFolder.trimEnd('/')}/$sub"
                    remoteFiles += scanFolder(creds, subPath, subPath, localByRemotePath)
                }

                _files.value = remoteFiles.sortedByDescending { it.fileName }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load Nextcloud files"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun scanFolder(
        creds: com.brbrs.blik.auth.AuthCredentials,
        folderPath: String,
        remoteFolder: String,
        localByRemotePath: Map<String, com.brbrs.blik.data.local.ScreenshotEntity>,
    ): List<RemoteFile> {
        val imageExtensions = setOf("png", "jpg", "jpeg", "webp")
        val davBase = "${creds.serverUrl}/remote.php/dav/files/${creds.username}"
        val authHeader = okhttp3.Credentials.basic(creds.username, creds.appPassword)
        return try {
            webDavClient.listFiles(creds, folderPath)
                .filter { it.substringAfterLast('.').lowercase() in imageExtensions }
                .map { fileName ->
                    val remotePath = "${folderPath.trimEnd('/')}/$fileName"
                    val local = localByRemotePath[remotePath]
                    RemoteFile(
                        fileName           = fileName,
                        remotePath         = remotePath,
                        remoteFolder       = remoteFolder,
                        isAvailableLocally = local != null,
                        localPath          = local?.localPath,
                        webDavUrl          = "$davBase$remotePath",
                        authHeader         = authHeader,
                    )
                }
        } catch (e: Exception) { emptyList() }
    }

    // ── Selection ─────────────────────────────────────────────────────────────
    fun toggleSelection(remotePath: String) {
        _selectedPaths.value = _selectedPaths.value.toMutableSet().apply {
            if (contains(remotePath)) remove(remotePath) else add(remotePath)
        }
    }

    fun selectAll() {
        _selectedPaths.value = _files.value.map { it.remotePath }.toSet()
    }

    fun clearSelection() { _selectedPaths.value = emptySet() }

    fun showDeleteDialog()  { _showDelete.value = true }
    fun dismissDeleteDialog() { _showDelete.value = false }

    /** Delete selected files from Nextcloud only. */
    fun deleteSelectedFromNextcloud() {
        val paths = _selectedPaths.value.toSet()
        viewModelScope.launch {
            _showDelete.value = false
            _isLoading.value  = true
            try {
                val creds = authManager.credentials.firstOrNull() ?: return@launch
                for (path in paths) {
                    try { webDavClient.deleteFile(creds, path) } catch (e: Exception) { /* continue */ }
                    // Also delete .md sidecar
                    val mdPath = path.substringBeforeLast(".") + ".md"
                    try { webDavClient.deleteFile(creds, mdPath) } catch (e: Exception) { }
                    // Update local DB record if present
                    val local = dao.observeAll().firstOrNull()?.find { it.remotePath == path }
                    if (local != null) {
                        dao.update(local.copy(
                            uploadStatus = UploadStatus.PENDING,
                            remotePath   = "",
                            remoteFolder = "",
                        ))
                    }
                }
                _selectedPaths.value = emptySet()
                refresh()
            } catch (e: Exception) {
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }

    fun dismissError() { _error.value = null }

    /** Delete a single file directly (from the info sheet). */
    fun deleteFileDirectly(file: RemoteFile) {
        viewModelScope.launch {
            try {
                val creds = authManager.credentials.firstOrNull() ?: return@launch
                webDavClient.deleteFile(creds, file.remotePath)
                val mdPath = file.remotePath.substringBeforeLast(".") + ".md"
                try { webDavClient.deleteFile(creds, mdPath) } catch (e: Exception) { }
                val local = dao.observeAll().firstOrNull()?.find { it.remotePath == file.remotePath }
                if (local != null) {
                    dao.update(local.copy(uploadStatus = UploadStatus.PENDING,
                        remotePath = "", remoteFolder = ""))
                }
                refresh()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
}
