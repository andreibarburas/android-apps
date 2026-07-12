package com.brbrs.blik.ui.screens.gallery

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.brbrs.blik.data.local.ScreenshotEntity
import com.brbrs.blik.data.local.UploadStatus
import com.brbrs.blik.data.repository.ScreenshotRepository
import com.brbrs.blik.data.repository.SettingsRepository
import com.brbrs.blik.ui.theme.ThemeRepository
import com.brbrs.blik.worker.UploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

enum class GalleryFilter { ALL, PENDING, UPLOADED, NOTES, STARRED }

data class GalleryUiState(
    val screenshots: List<ScreenshotEntity> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedFilter: GalleryFilter = GalleryFilter.ALL,
    val selectedCategory: String? = null,
    val searchQuery: String = "",
    val isDark: Boolean = true,
    val isScanning: Boolean = false,
    val scanError: String? = null,
    val selectedPaths: Set<String> = emptySet(),
    val snackbarMessage: String? = null,
    /** Non-null when Android 11+ requires a system delete confirmation dialog. */
    val pendingDeleteSender: android.content.IntentSender? = null,
) {
    val isSelecting: Boolean get() = selectedPaths.isNotEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GalleryViewModel @Inject constructor(
    app: Application,
    private val repo: ScreenshotRepository,
    private val settings: SettingsRepository,
    private val themeRepo: ThemeRepository,
) : AndroidViewModel(app) {

    private val _filter        = MutableStateFlow(GalleryFilter.ALL)
    private val _category      = MutableStateFlow<String?>(null)
    private val _search        = MutableStateFlow("")
    private val _scanning      = MutableStateFlow(false)
    private val _scanErr       = MutableStateFlow<String?>(null)
    private val _selectedPaths = MutableStateFlow<Set<String>>(emptySet())
    private val _snackbar      = MutableStateFlow<String?>(null)
    private val _pendingDelete = MutableStateFlow<android.content.IntentSender?>(null)
    // Entities awaiting system delete confirmation
    private var _pendingDeleteEntities: List<ScreenshotEntity> = emptyList()

    // Refresh categories whenever the screenshot list changes
    private val _categories: StateFlow<List<String>> = repo.observeAll()
        .map { _ -> repo.getAllCategories() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _screenshots: Flow<List<ScreenshotEntity>> = combine(
        _filter, _category, _search,
    ) { filter, category, search -> Triple(filter, category, search) }
        .flatMapLatest { (filter, category, search) ->
            when {
                search.isNotBlank() -> repo.search(search)
                category != null    -> repo.filterByCategory(category)
                filter == GalleryFilter.PENDING  -> repo.observePending()
                filter == GalleryFilter.UPLOADED -> repo.observeUploaded()
                filter == GalleryFilter.NOTES    -> repo.observeWithNotes()
                filter == GalleryFilter.STARRED  -> repo.observeStarred()
                else                             -> repo.observeAll()
            }
        }

    init {
        // Auto-scan local folder each time the app is opened
        viewModelScope.launch {
            val folder = settings.localFolder.firstOrNull() ?: return@launch
            if (folder.isNotBlank()) {
                try { repo.scanLocalFolder(folder) } catch (e: Exception) { /* silent */ }
            }
        }
    }

    val uiState: StateFlow<GalleryUiState> = combine(
        _screenshots,
        _categories,
        _filter,
        _category,
        _search,
    ) { screenshots, categories, filter, category, search ->
        GalleryUiState(
            screenshots      = screenshots,
            categories       = categories,
            selectedFilter   = filter,
            selectedCategory = category,
            searchQuery      = search,
        )
    }.combine(themeRepo.isDark) { state, dark ->
        state.copy(isDark = dark)
    }.combine(_scanning) { state, scanning ->
        state.copy(isScanning = scanning)
    }.combine(_scanErr) { state, err ->
        state.copy(scanError = err)
    }.combine(_selectedPaths) { state, sel ->
        state.copy(selectedPaths = sel)
    }.combine(_snackbar) { state, msg ->
        state.copy(snackbarMessage = msg)
    }.combine(_pendingDelete) { state, sender ->
        state.copy(pendingDeleteSender = sender)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GalleryUiState())

    fun onFilterSelected(filter: GalleryFilter) { _filter.value = filter; _category.value = null }
    fun onCategorySelected(cat: String?) { _category.value = cat }
    fun onSearchChanged(q: String) { _search.value = q }
    fun dismissScanError() { _scanErr.value = null }

    // ── Multi-select ──────────────────────────────────────────────────────────
    fun toggleSelection(path: String) {
        _selectedPaths.value = _selectedPaths.value.toMutableSet().apply {
            if (contains(path)) remove(path) else add(path)
        }
    }

    fun selectAll() {
        _selectedPaths.value = uiState.value.screenshots.map { it.localPath }.toSet()
    }

    fun clearSelection() {
        _selectedPaths.value = emptySet()
    }

    fun deleteSelected() {
        val paths = _selectedPaths.value.toSet()
        if (paths.isEmpty()) return
        viewModelScope.launch {
            val all = repo.observeAll().firstOrNull() ?: emptyList()
            val toDelete = all.filter { it.localPath in paths }
            val sender = repo.deleteLocalFiles(toDelete)
            if (sender != null) {
                _pendingDeleteEntities = toDelete
                _pendingDelete.value = sender
                // Records deleted after system dialog confirms via onDeleteConfirmed()
            } else {
                _selectedPaths.value = emptySet()
            }
        }
    }

    /** Called after the system delete dialog returns RESULT_OK. */
    fun onDeleteConfirmed() {
        viewModelScope.launch {
            repo.deleteRecords(_pendingDeleteEntities)
            _pendingDeleteEntities = emptyList()
            _pendingDelete.value = null
            _selectedPaths.value = emptySet()
        }
    }

    /** Called if the user cancelled the system delete dialog. */
    fun onDeleteCancelled() {
        _pendingDeleteEntities = emptyList()
        _pendingDelete.value = null
    }

    fun toggleBlur(entity: ScreenshotEntity) {
        viewModelScope.launch { repo.toggleBlur(entity) }
    }

    fun toggleStar(entity: ScreenshotEntity) {
        viewModelScope.launch { repo.toggleStar(entity) }
    }

    /** In multi-select: star all if any are unstarred, otherwise unstar all. */
    fun toggleStarSelected() {
        val paths = _selectedPaths.value.toSet()
        if (paths.isEmpty()) return
        viewModelScope.launch {
            val all = repo.observeAll().firstOrNull() ?: return@launch
            val selected = all.filter { it.localPath in paths }
            val shouldStar = selected.any { !it.isStarred }
            selected.forEach { repo.toggleStarTo(it, shouldStar) }
        }
    }

    /** In multi-select: blur all selected if any are unblurred, otherwise unblur all. */
    fun toggleBlurSelected() {
        val paths = _selectedPaths.value.toSet()
        if (paths.isEmpty()) return
        viewModelScope.launch {
            val all = repo.observeAll().firstOrNull() ?: return@launch
            val selected = all.filter { it.localPath in paths }
            // If any are not blurred → blur all; if all blurred → unblur all
            val shouldBlur = selected.any { !it.isBlurred }
            repo.toggleBlurSelected(paths, shouldBlur)
        }
    }

    fun uploadSelected() {
        val paths = _selectedPaths.value.toSet()
        if (paths.isEmpty()) return
        viewModelScope.launch {
            val wifiOnly     = settings.wifiOnly.firstOrNull() ?: true
            val chargingOnly = settings.chargingOnly.firstOrNull() ?: false
            val ctx          = getApplication<Application>()
            if (wifiOnly && !isOnWifi(ctx)) {
                _scanErr.value = "Upload requires Wi-Fi. Connect or change settings."
                return@launch
            }
            if (chargingOnly && !isCharging(ctx)) {
                _scanErr.value = "Upload requires charging. Plug in or change settings."
                return@launch
            }
            val onConflict = settings.onConflict.firstOrNull() ?: "ASK"
            val all = repo.observeAll().firstOrNull() ?: emptyList()
            var succeeded = 0; var failed = 0
            all.filter { it.localPath in paths }.forEach {
                val result = repo.uploadScreenshot(it, onConflict)
                if (result.isSuccess) succeeded++ else failed++
            }
            _selectedPaths.value = emptySet()
            _snackbar.value = when {
                failed == 0    -> "✓ $succeeded screenshot${if (succeeded != 1) "s" else ""} uploaded"
                succeeded == 0 -> "✗ All $failed uploads failed"
                else           -> "✓ $succeeded uploaded · ✗ $failed failed"
            }
        }
    }

    fun toggleTheme() {
        viewModelScope.launch { themeRepo.setDark(!themeRepo.isDark.first()) }
    }

    fun scanFolder() {
        viewModelScope.launch {
            _scanning.value = true
            _scanErr.value  = null
            try {
                val folder = settings.localFolder.firstOrNull() ?: ""
                if (folder.isBlank()) {
                    _scanErr.value = "No screenshot folder set. Go to Settings → Folders."
                    return@launch
                }
                repo.scanLocalFolder(folder)
                repo.scanMediaStore(folder)
            } catch (e: Exception) {
                _scanErr.value = e.message
            } finally {
                _scanning.value = false
            }
        }
    }

    fun dismissSnackbar() { _snackbar.value = null }

    fun uploadSingle(entity: ScreenshotEntity) {
        viewModelScope.launch {
            val onConflict = settings.onConflict.firstOrNull() ?: "ASK"
            val result = repo.uploadScreenshot(entity, onConflict)
            _snackbar.value = if (result.isSuccess)
                "✓ ${entity.fileName} uploaded"
            else
                "✗ Upload failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
        }
    }

    fun uploadAllPending() {
        viewModelScope.launch {
            val wifiOnly     = settings.wifiOnly.firstOrNull() ?: true
            val chargingOnly = settings.chargingOnly.firstOrNull() ?: false
            val ctx          = getApplication<Application>()

            if (wifiOnly && !isOnWifi(ctx)) {
                _scanErr.value = "Upload requires Wi-Fi. Connect or change settings."
                return@launch
            }
            if (chargingOnly && !isCharging(ctx)) {
                _scanErr.value = "Upload requires charging. Plug in or change settings."
                return@launch
            }

            val onConflict = settings.onConflict.firstOrNull() ?: "ASK"
            val pending    = repo.observePending().firstOrNull() ?: emptyList()
            var succeeded  = 0
            var failed     = 0
            for (entity in pending) {
                val result = repo.uploadScreenshot(entity, onConflict)
                if (result.isSuccess) succeeded++ else failed++
            }
            _snackbar.value = when {
                failed == 0  -> "✓ $succeeded screenshot${if (succeeded != 1) "s" else ""} uploaded"
                succeeded == 0 -> "✗ All $failed uploads failed"
                else           -> "✓ $succeeded uploaded · ✗ $failed failed"
            }
        }
    }

    fun scheduleAutoUpload() {
        viewModelScope.launch {
            val wifiOnly     = settings.wifiOnly.firstOrNull() ?: true
            val chargingOnly = settings.chargingOnly.firstOrNull() ?: false
            val constraints  = Constraints.Builder().apply {
                if (wifiOnly) setRequiredNetworkType(NetworkType.UNMETERED)
                else          setRequiredNetworkType(NetworkType.CONNECTED)
                if (chargingOnly) setRequiresCharging(true)
            }.build()
            val request = PeriodicWorkRequestBuilder<UploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints).build()
            WorkManager.getInstance(getApplication())
                .enqueueUniquePeriodicWork("blik_auto_upload", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }

    private fun isOnWifi(ctx: Context): Boolean {
        val cm  = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun isCharging(ctx: Context): Boolean {
        // BatteryManager.isCharging can return false even when plugged in on some devices.
        // The ACTION_BATTERY_CHANGED sticky broadcast is the reliable cross-device way.
        val intent = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            .let { ctx.registerReceiver(null, it) }
        val status = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
            || status == android.os.BatteryManager.BATTERY_STATUS_FULL
    }
}
