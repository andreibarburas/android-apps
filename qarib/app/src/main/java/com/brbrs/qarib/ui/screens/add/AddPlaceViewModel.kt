package com.brbrs.qarib.ui.screens.add

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.qarib.data.local.PhotoStorage
import com.brbrs.qarib.data.remote.NominatimClient
import com.brbrs.qarib.data.repository.PlacesRepository
import com.brbrs.qarib.data.sync.SyncScheduler
import com.brbrs.qarib.domain.model.GeocodeResult
import com.brbrs.qarib.domain.model.deriveCountryFromAddress
import com.brbrs.qarib.domain.model.newPlace
import com.brbrs.qarib.ui.navigation.QaribRoute
import com.brbrs.qarib.ui.theme.PlaceCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AddPlaceUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<GeocodeResult> = emptyList(),
    val searchError: String? = null,
    val selectedResult: GeocodeResult? = null,
    val category: PlaceCategory = PlaceCategory.RESTAURANT,
    val note: String = "",
    val photoPath: String = "",
    val isSaved: Boolean = false
)

@HiltViewModel
class AddPlaceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val nominatimClient: NominatimClient,
    private val placesRepository: PlacesRepository,
    private val photoStorage: PhotoStorage,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    /**
     * Generated up front so an attached photo can be stored under this
     * place's id before the place itself is saved.
     */
    private val placeId: String = UUID.randomUUID().toString()

    private val _uiState = MutableStateFlow(AddPlaceUiState())
    val uiState: StateFlow<AddPlaceUiState> = _uiState.asStateFlow()

    init {
        val initialQuery: String? = savedStateHandle[QaribRoute.INITIAL_QUERY_ARG]
        if (!initialQuery.isNullOrBlank()) {
            _uiState.update { it.copy(query = initialQuery) }
            search()
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query, selectedResult = null, searchError = null) }
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchError = null, results = emptyList()) }

            when (val result = nominatimClient.search(query)) {
                is NominatimClient.Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            results = result.results,
                            searchError = if (result.results.isEmpty()) "no_results" else null
                        )
                    }
                }
                is NominatimClient.Result.Error -> {
                    _uiState.update {
                        it.copy(isSearching = false, searchError = result.message)
                    }
                }
            }
        }
    }

    fun selectResult(result: GeocodeResult) {
        _uiState.update {
            it.copy(
                selectedResult = result,
                query = result.displayName,
                results = emptyList()
            )
        }
    }

    fun onCategorySelected(category: PlaceCategory) {
        _uiState.update { it.copy(category = category) }
    }

    fun onNoteChange(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    /** Saves [uri] (from gallery picker) as this place's photo. */
    fun onPhotoPicked(uri: Uri) {
        viewModelScope.launch {
            val newPath = photoStorage.savePhoto(uri, placeId, previousPhotoPath = _uiState.value.photoPath.ifBlank { null })
            if (newPath != null) {
                _uiState.update { it.copy(photoPath = newPath) }
            }
        }
    }

    /** Creates a camera capture target for this place's photo. */
    fun createCameraCaptureTarget(): PhotoStorage.CameraCapture = photoStorage.createCameraCaptureTarget(placeId)

    /** Processes a completed camera capture and sets it as this place's photo. */
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

    fun save() {
        val state = _uiState.value
        val selected = state.selectedResult ?: return

        viewModelScope.launch {
            val place = newPlace(
                id = placeId,
                name = displayNameForPlace(selected.displayName),
                category = state.category,
                latitude = selected.latitude,
                longitude = selected.longitude,
                address = selected.address,
                note = state.note.trim(),
                country = selected.country.ifBlank { deriveCountryFromAddress(selected.address) },
                photoPath = state.photoPath,
            )
            placesRepository.savePlace(place)
            syncScheduler.requestSync()
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    /**
     * Nominatim's display_name is a full address string. Use the first
     * comma-separated component as the place name, which is typically
     * the venue or street name.
     */
    private fun displayNameForPlace(displayName: String): String =
        displayName.split(",").firstOrNull()?.trim().takeUnless { it.isNullOrEmpty() } ?: displayName
}
