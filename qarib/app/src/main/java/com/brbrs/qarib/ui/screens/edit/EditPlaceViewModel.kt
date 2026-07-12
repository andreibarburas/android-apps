package com.brbrs.qarib.ui.screens.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.qarib.data.local.PhotoStorage
import com.brbrs.qarib.data.remote.NominatimClient
import com.brbrs.qarib.data.repository.PlacesRepository
import com.brbrs.qarib.data.sync.SyncScheduler
import com.brbrs.qarib.domain.model.GeocodeResult
import com.brbrs.qarib.domain.model.Place
import com.brbrs.qarib.domain.model.deriveCountryFromAddress
import com.brbrs.qarib.ui.navigation.QaribRoute
import com.brbrs.qarib.ui.theme.DEFAULT_GEOFENCE_RADIUS_METERS
import com.brbrs.qarib.ui.theme.DisplayPreferencesRepository
import com.brbrs.qarib.ui.theme.PlaceCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditPlaceUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val category: PlaceCategory = PlaceCategory.RESTAURANT,
    val note: String = "",
    val address: String = "",
    val country: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val photoPath: String = "",
    val geofenceRadiusMeters: Int? = null,
    val defaultRadiusMeters: Int = DEFAULT_GEOFENCE_RADIUS_METERS,
    // Location re-search state
    val locationQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<GeocodeResult> = emptyList(),
    val searchError: String? = null,
    val isSaved: Boolean = false,
)

@HiltViewModel
class EditPlaceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val placesRepository: PlacesRepository,
    private val nominatimClient: NominatimClient,
    private val photoStorage: PhotoStorage,
    private val displayPrefs: DisplayPreferencesRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val placeId: String = checkNotNull(savedStateHandle[QaribRoute.PLACE_ID_ARG])

    private val _uiState = MutableStateFlow(EditPlaceUiState())
    val uiState: StateFlow<EditPlaceUiState> = _uiState.asStateFlow()

    private var original: Place? = null

    init {
        viewModelScope.launch {
            val place = placesRepository.places.first().firstOrNull { it.id == placeId }
            val defaultRadius = displayPrefs.preferences.first().geofenceRadiusMeters
            if (place != null) {
                original = place
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        name = place.name,
                        category = place.category,
                        note = place.note,
                        address = place.address,
                        country = place.country,
                        latitude = place.latitude,
                        longitude = place.longitude,
                        photoPath = place.photoPath,
                        geofenceRadiusMeters = place.geofenceRadiusMeters,
                        defaultRadiusMeters = defaultRadius,
                        locationQuery = place.address,
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, defaultRadiusMeters = defaultRadius) }
            }
        }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun onCategorySelected(category: PlaceCategory) {
        _uiState.update { it.copy(category = category) }
    }

    fun onNoteChange(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    /** Sets a per-place notification radius override, or null to use the global default. */
    fun onGeofenceRadiusChange(radiusMeters: Int?) {
        _uiState.update { it.copy(geofenceRadiusMeters = radiusMeters) }
    }

    fun onLocationQueryChange(query: String) {
        _uiState.update { it.copy(locationQuery = query, searchResults = emptyList(), searchError = null) }
    }

    /** Saves [uri] (from gallery picker) as this place's photo, replacing any previous one. */
    fun onPhotoPicked(uri: Uri) {
        viewModelScope.launch {
            val previous = _uiState.value.photoPath.ifBlank { null }
            val newPath = photoStorage.savePhoto(uri, placeId, previousPhotoPath = previous)
            if (newPath != null) {
                _uiState.update { it.copy(photoPath = newPath) }
            }
        }
    }

    /** Creates a camera capture target for this place's photo. */
    fun createCameraCaptureTarget(): PhotoStorage.CameraCapture = photoStorage.createCameraCaptureTarget(placeId)

    /** Processes a completed camera capture and sets it as this place's photo, replacing any previous one. */
    fun onCameraCaptureComplete(capture: PhotoStorage.CameraCapture) {
        viewModelScope.launch {
            val previous = _uiState.value.photoPath.ifBlank { null }
            val processedPath = photoStorage.processCameraCapture(capture.file)
            if (processedPath != null) {
                if (!previous.isNullOrBlank()) photoStorage.deletePhoto(previous)
                _uiState.update { it.copy(photoPath = processedPath) }
            }
        }
    }

    fun removePhoto() {
        viewModelScope.launch {
            val current = _uiState.value.photoPath
            if (current.isNotBlank()) {
                photoStorage.deletePhoto(current)
                _uiState.update { it.copy(photoPath = "") }
            }
        }
    }

    fun searchLocation() {
        val query = _uiState.value.locationQuery.trim()
        if (query.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchError = null, searchResults = emptyList()) }
            when (val result = nominatimClient.search(query)) {
                is NominatimClient.Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            searchResults = result.results,
                            searchError = if (result.results.isEmpty()) "no_results" else null
                        )
                    }
                }
                is NominatimClient.Result.Error -> {
                    _uiState.update { it.copy(isSearching = false, searchError = result.message) }
                }
            }
        }
    }

    fun selectLocation(result: GeocodeResult) {
        _uiState.update {
            it.copy(
                address = result.address,
                country = result.country.ifBlank { deriveCountryFromAddress(result.address) },
                latitude = result.latitude,
                longitude = result.longitude,
                locationQuery = result.displayName,
                searchResults = emptyList(),
            )
        }
    }

    fun save() {
        val state = _uiState.value
        val base = original ?: return
        if (state.name.isBlank()) return

        viewModelScope.launch {
            val updated = base.copy(
                name = state.name.trim(),
                category = state.category,
                note = state.note.trim(),
                address = state.address,
                country = state.country,
                latitude = state.latitude,
                longitude = state.longitude,
                photoPath = state.photoPath,
                geofenceRadiusMeters = state.geofenceRadiusMeters,
            )
            placesRepository.updatePlace(updated)
            syncScheduler.requestSync()
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
