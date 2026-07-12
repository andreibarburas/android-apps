package com.brbrs.qarib.ui.screens.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.qarib.data.repository.PlacesRepository
import com.brbrs.qarib.data.sync.SyncScheduler
import com.brbrs.qarib.domain.model.Place
import com.brbrs.qarib.geofence.GeofenceManager
import com.brbrs.qarib.ui.theme.DisplayPreferencesRepository
import com.brbrs.qarib.ui.theme.PlaceCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PlacesView { MAP, LIST }

/** A country section in the grouped list, with active and visited places. */
data class CountrySection(
    val country: String,
    val activePlaces: List<Place>,
    val visitedPlaces: List<Place>,
)

data class PlacesUiState(
    val view: PlacesView = PlacesView.MAP,
    val searchQuery: String = "",
    val selectedCategories: Set<PlaceCategory> = emptySet(),
    val showVisited: Boolean = true,
)

@HiltViewModel
class PlacesViewModel @Inject constructor(
    private val placesRepository: PlacesRepository,
    private val geofenceManager: GeofenceManager,
    private val displayPrefs: DisplayPreferencesRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val allPlaces: StateFlow<List<Place>> = placesRepository.places
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(PlacesUiState())
    val uiState: StateFlow<PlacesUiState> = _uiState.asStateFlow()

    /** Current theme mode string ("system", "light", "dark") — for the top-bar toggle. */
    val themeMode: StateFlow<String> = displayPrefs.preferences
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    /** Places matching the current search/category/visited filters, for the map. */
    val filteredPlaces: StateFlow<List<Place>> = combine(allPlaces, _uiState) { places, state ->
        applyFilters(places, state, includeVisited = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Places grouped by country (sorted), each split into active/visited, for the list view. */
    val countrySections: StateFlow<List<CountrySection>> = combine(allPlaces, _uiState) { places, state ->
        val filtered = applyFilters(places, state, includeVisited = true)
        groupByCountry(filtered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            placesRepository.backfillCountries()
        }

        // Silent background sync on first load — picks up changes made on
        // other devices since this device was last opened.
        syncScheduler.syncNow()

        // Keep registered geofences in sync with the saved places list and
        // the global radius preference. Muted and visited places are
        // excluded. No-op if location permission is not granted.
        viewModelScope.launch {
            combine(
                allPlaces,
                displayPrefs.preferences,
            ) { places, prefs -> places to prefs.geofenceRadiusMeters }
                .collect { (places, radius) ->
                    val eligible = places.filter { !it.notificationsMuted && !it.visited }
                    geofenceManager.syncGeofences(eligible, radius)
                }
        }
    }

    /** Cycles: system → light → dark → system. */
    fun toggleTheme() {
        val next = when (themeMode.value) {
            "light" -> "dark"
            "dark" -> "system"
            else -> "light"
        }
        viewModelScope.launch { displayPrefs.setThemeMode(next) }
    }

    fun setView(view: PlacesView) {
        _uiState.update { it.copy(view = view) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleCategory(category: PlaceCategory) {
        _uiState.update {
            val current = it.selectedCategories
            val updated = if (category in current) current - category else current + category
            it.copy(selectedCategories = updated)
        }
    }

    fun setShowVisited(show: Boolean) {
        _uiState.update { it.copy(showVisited = show) }
    }

    fun deletePlace(id: String) {
        viewModelScope.launch {
            placesRepository.deletePlace(id)
            syncScheduler.requestSync()
        }
    }

    fun setVisited(id: String, visited: Boolean) {
        viewModelScope.launch {
            placesRepository.setVisited(id, visited)
            syncScheduler.requestSync()
        }
    }

    fun setNotificationsMuted(id: String, muted: Boolean) {
        viewModelScope.launch {
            placesRepository.setNotificationsMuted(id, muted)
            syncScheduler.requestSync()
        }
    }

    private fun applyFilters(places: List<Place>, state: PlacesUiState, includeVisited: Boolean): List<Place> {
        var result = places
        if (state.searchQuery.isNotBlank()) {
            val q = state.searchQuery.trim().lowercase()
            result = result.filter { it.name.lowercase().contains(q) || it.country.lowercase().contains(q) }
        }
        if (state.selectedCategories.isNotEmpty()) {
            result = result.filter { it.category in state.selectedCategories }
        }
        if (!state.showVisited) {
            result = result.filter { !it.visited }
        }
        return result
    }

    private fun groupByCountry(places: List<Place>): List<CountrySection> {
        return places
            .groupBy { it.country.ifBlank { "—" } }
            .toSortedMap(compareBy { it })
            .map { (country, group) ->
                val active = group.filter { !it.visited }.sortedBy { it.name.lowercase() }
                val visited = group.filter { it.visited }.sortedBy { it.name.lowercase() }
                CountrySection(country = country, activePlaces = active, visitedPlaces = visited)
            }
            .filter { it.activePlaces.isNotEmpty() || it.visitedPlaces.isNotEmpty() }
    }
}
