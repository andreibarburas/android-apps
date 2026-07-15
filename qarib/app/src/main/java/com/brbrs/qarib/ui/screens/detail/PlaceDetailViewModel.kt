package com.brbrs.qarib.ui.screens.detail

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.qarib.data.local.PhotoStorage
import com.brbrs.qarib.data.repository.PlacesRepository
import com.brbrs.qarib.data.sync.SyncScheduler
import com.brbrs.qarib.domain.model.Place
import com.brbrs.qarib.domain.model.Visit
import com.brbrs.qarib.domain.model.newVisit
import com.brbrs.qarib.ui.navigation.QaribRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaceDetailUiState(
    val place: Place? = null,
    val isLoading: Boolean = true,
    val showDeleteConfirm: Boolean = false,
    val showAddVisitSheet: Boolean = false,
    val editingVisit: Visit? = null,
)

@HiltViewModel
class PlaceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val placesRepository: PlacesRepository,
    private val photoStorage: PhotoStorage,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val placeId: String = checkNotNull(savedStateHandle[QaribRoute.PLACE_ID_ARG])

    private val _uiState = MutableStateFlow(PlaceDetailUiState())
    val uiState: StateFlow<PlaceDetailUiState> = _uiState.asStateFlow()

    val visits: StateFlow<List<Visit>> = placesRepository.visitsForPlace(placeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            placesRepository.places.collect { places ->
                val place = places.firstOrNull { it.id == placeId }
                _uiState.update { it.copy(place = place, isLoading = false) }
            }
        }
    }

    fun toggleVisited() {
        val place = _uiState.value.place ?: return
        viewModelScope.launch {
            placesRepository.setVisited(placeId, !place.visited)
            syncScheduler.requestSync()
        }
    }

    fun toggleMuted() {
        val place = _uiState.value.place ?: return
        viewModelScope.launch {
            placesRepository.setNotificationsMuted(placeId, !place.notificationsMuted)
            syncScheduler.requestSync()
        }
    }

    fun deletePlace() {
        viewModelScope.launch {
            placesRepository.deletePlace(placeId)
            syncScheduler.requestSync()
        }
    }

    fun showDeleteConfirm(show: Boolean) {
        _uiState.update { it.copy(showDeleteConfirm = show) }
    }

    fun startAddVisit() {
        val draft = newVisit(placeId)
        _uiState.update { it.copy(editingVisit = draft, showAddVisitSheet = true) }
    }

    fun startEditVisit(visit: Visit) {
        _uiState.update { it.copy(editingVisit = visit, showAddVisitSheet = true) }
    }

    fun dismissVisitSheet() {
        _uiState.update { it.copy(editingVisit = null, showAddVisitSheet = false) }
    }

    fun updateEditingVisitNote(note: String) {
        _uiState.update { it.copy(editingVisit = it.editingVisit?.copy(note = note)) }
    }

    fun updateEditingVisitDate(epochMillis: Long) {
        _uiState.update { it.copy(editingVisit = it.editingVisit?.copy(visitedAt = epochMillis)) }
    }

    fun addPhotoToEditingVisit(uri: Uri) {
        val visit = _uiState.value.editingVisit ?: return
        viewModelScope.launch {
            val path = photoStorage.saveVisitPhoto(uri, visit.id) ?: return@launch
            val updated = visit.copy(photoPaths = visit.photoPaths + path)
            _uiState.update { it.copy(editingVisit = updated) }
        }
    }

    fun removePhotoFromEditingVisit(path: String) {
        val visit = _uiState.value.editingVisit ?: return
        viewModelScope.launch {
            photoStorage.deletePhoto(path)
            val updated = visit.copy(photoPaths = visit.photoPaths - path)
            _uiState.update { it.copy(editingVisit = updated) }
        }
    }

    fun saveVisit() {
        val visit = _uiState.value.editingVisit ?: return
        viewModelScope.launch {
            placesRepository.saveVisit(visit)
            syncScheduler.requestSync()
            _uiState.update { it.copy(editingVisit = null, showAddVisitSheet = false) }
        }
    }

    fun deleteVisit(visitId: String) {
        viewModelScope.launch {
            placesRepository.deleteVisit(visitId)
            syncScheduler.requestSync()
        }
    }
}
