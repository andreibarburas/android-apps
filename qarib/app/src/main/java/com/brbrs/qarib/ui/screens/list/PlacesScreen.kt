package com.brbrs.qarib.ui.screens.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.qarib.R
import com.brbrs.qarib.domain.model.Place
import com.brbrs.qarib.ui.components.PlaceDetailSheet
import com.brbrs.qarib.ui.components.PlaceListItem
import com.brbrs.qarib.ui.screens.map.OsmMapView
import com.brbrs.qarib.ui.theme.LocalIsDark
import com.brbrs.qarib.ui.theme.PlaceCategory
import com.brbrs.qarib.ui.theme.categoryColor
import com.brbrs.qarib.ui.theme.icon
import com.brbrs.qarib.ui.theme.labelRes
import com.brbrs.qarib.ui.theme.qaribBackground
import com.brbrs.qarib.ui.theme.qaribChip
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(
    onAddPlace: (String) -> Unit,
    onEditPlace: (String) -> Unit,
    onSettings: () -> Unit,
    viewModel: PlacesViewModel = hiltViewModel()
) {
    val mapPlaces by viewModel.filteredPlaces.collectAsState()
    val sections by viewModel.countrySections.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    var selectedPlace by remember { mutableStateOf<Place?>(null) }
    var showMoreFilters by remember { mutableStateOf(false) }
    val isDark = LocalIsDark.current

    // Theme toggle icon: sun = currently light (click → dark), moon = currently dark (click → light),
    // brightness_auto = system mode (click → light).
    val themeIcon = when (themeMode) {
        "light" -> Icons.Outlined.LightMode
        "dark" -> Icons.Outlined.DarkMode
        else -> Icons.Outlined.LightMode
    }
    val themeContentDescription = when (themeMode) {
        "light" -> "Light mode"
        "dark" -> "Dark mode"
        else -> "System theme"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Image(
                        painter = painterResource(id = R.drawable.qarib_logo),
                        contentDescription = stringResource(R.string.app_name),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                        modifier = Modifier.height(28.dp),
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleTheme() }) {
                        Icon(themeIcon, contentDescription = themeContentDescription)
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background
            ) {
                NavigationBarItem(
                    selected = uiState.view == PlacesView.MAP,
                    onClick = { viewModel.setView(PlacesView.MAP) },
                    icon = { Icon(Icons.Outlined.Map, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_map)) }
                )
                NavigationBarItem(
                    selected = uiState.view == PlacesView.LIST,
                    onClick = { viewModel.setView(PlacesView.LIST) },
                    icon = { Icon(Icons.Outlined.Place, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_list)) }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAddPlace("") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_place_title))
            }
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .qaribBackground(isDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Search bar
                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                )

                // Category filter chips + more filters
                CategoryFilterRow(
                    selected = uiState.selectedCategories,
                    onToggle = viewModel::toggleCategory,
                    onMoreFilters = { showMoreFilters = true },
                    isDark = isDark,
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                    if (mapPlaces.isEmpty() && sections.isEmpty()) {
                        EmptyState(
                            hasFilters = uiState.searchQuery.isNotBlank() || uiState.selectedCategories.isNotEmpty(),
                            searchQuery = uiState.searchQuery,
                            onAddPlace = onAddPlace,
                        )
                    } else {
                        when (uiState.view) {
                            PlacesView.MAP -> {
                                OsmMapView(
                                    places = mapPlaces,
                                    onMarkerClick = { selectedPlace = it },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            PlacesView.LIST -> {
                                CountryGroupedList(
                                    sections = sections,
                                    onPlaceClick = { selectedPlace = it },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedPlace?.let { place ->
        PlaceDetailSheet(
            place = place,
            onDismiss = { selectedPlace = null },
            onDelete = { viewModel.deletePlace(it.id) },
            onEdit = {
                selectedPlace = null
                onEditPlace(it.id)
            },
            onToggleVisited = { viewModel.setVisited(it.id, !it.visited) },
            onToggleMuted = { viewModel.setNotificationsMuted(it.id, !it.notificationsMuted) },
        )
    }

    if (showMoreFilters) {
        MoreFiltersSheet(
            selectedCategories = uiState.selectedCategories,
            showVisited = uiState.showVisited,
            onToggleCategory = viewModel::toggleCategory,
            onShowVisitedChange = viewModel::setShowVisited,
            onDismiss = { showMoreFilters = false },
            isDark = isDark,
        )
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(46.dp)
            .qaribPillBackground(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.places_search_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth(),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.places_search_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Modifier.qaribPillBackground(): Modifier {
    val isDark = LocalIsDark.current
    return this.then(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (isDark) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
    )
}

@Composable
private fun CategoryFilterRow(
    selected: Set<PlaceCategory>,
    onToggle: (PlaceCategory) -> Unit,
    onMoreFilters: () -> Unit,
    isDark: Boolean,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterIconChip(
                icon = Icons.Outlined.FilterList,
                selected = false,
                isDark = isDark,
                onClick = onMoreFilters,
                contentDescription = stringResource(R.string.places_more_filters),
            )
        }
        items(PlaceCategory.entries.toList()) { category ->
            CategoryChip(
                category = category,
                selected = category in selected,
                isDark = isDark,
                onClick = { onToggle(category) },
            )
        }
    }
}

@Composable
private fun FilterIconChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(50))
            .qaribChip(isDark = isDark, selected = selected)
            .clickableNoIndication(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun CategoryChip(
    category: PlaceCategory,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    val accent = categoryColor(category)
    Row(
        modifier = Modifier
            .height(36.dp)
            .qaribChip(isDark = isDark, selected = selected)
            .then(Modifier.clip(RoundedCornerShape(50)))
            .then(Modifier.clickableNoIndication(onClick))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = category.icon(),
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else accent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(category.labelRes()),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    return this.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
    )
}

/**
 * Sealed list-row model for the flattened country-grouped list, used so
 * the A-Z sidebar can index into the LazyColumn by item position.
 */
private sealed class ListRow {
    data class Header(val country: String) : ListRow()
    data class Item(val place: Place) : ListRow()
    data class VisitedLabel(val country: String) : ListRow()
}

@Composable
private fun CountryGroupedList(
    sections: List<CountrySection>,
    onPlaceClick: (Place) -> Unit,
) {
    val rows = remember(sections) {
        buildList {
            for (section in sections) {
                add(ListRow.Header(section.country))
                section.activePlaces.forEach { add(ListRow.Item(it)) }
                if (section.visitedPlaces.isNotEmpty()) {
                    add(ListRow.VisitedLabel(section.country))
                    section.visitedPlaces.forEach { add(ListRow.Item(it)) }
                }
            }
        }
    }

    // Index of the first row for each country, used by the A-Z sidebar.
    val countryToIndex = remember(rows) {
        val map = linkedMapOf<String, Int>()
        rows.forEachIndexed { index, row ->
            if (row is ListRow.Header) map.putIfAbsent(row.country, index)
        }
        map
    }

    val letters = remember(countryToIndex) {
        countryToIndex.keys
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .distinct()
            .sorted()
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(rows.size) { index ->
                when (val row = rows[index]) {
                    is ListRow.Header -> CountryHeader(country = row.country)
                    is ListRow.VisitedLabel -> VisitedSectionLabel()
                    is ListRow.Item -> PlaceListItem(
                        place = row.place,
                        onClick = { onPlaceClick(row.place) },
                        modifier = if (row.place.visited) Modifier.alpha(0.5f) else Modifier,
                    )
                }
            }
        }

        if (letters.size > 1) {
            AlphabetSidebar(
                letters = letters,
                onLetterSelected = { letter ->
                    val targetCountry = countryToIndex.keys.firstOrNull {
                        it.firstOrNull()?.uppercaseChar() == letter
                    }
                    val targetIndex = targetCountry?.let { countryToIndex[it] }
                    if (targetIndex != null) {
                        coroutineScope.launch {
                            listState.animateScrollToItem(targetIndex)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun CountryHeader(country: String) {
    Text(
        text = country,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun VisitedSectionLabel() {
    Text(
        text = stringResource(R.string.places_visited_section_label),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun AlphabetSidebar(
    letters: List<Char>,
    onLetterSelected: (Char) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(end = 4.dp, top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        letters.forEach { letter ->
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .then(Modifier.clickableNoIndication { onLetterSelected(letter) }),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreFiltersSheet(
    selectedCategories: Set<PlaceCategory>,
    showVisited: Boolean,
    onToggleCategory: (PlaceCategory) -> Unit,
    onShowVisitedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    isDark: Boolean,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.places_more_filters),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.add_place_category_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            PlaceCategory.entries.forEach { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(Modifier.clickableNoIndication { onToggleCategory(category) })
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = category in selectedCategories,
                        onCheckedChange = { onToggleCategory(category) },
                    )
                    Icon(
                        imageVector = category.icon(),
                        contentDescription = null,
                        tint = categoryColor(category),
                        modifier = Modifier
                            .size(18.dp)
                            .padding(horizontal = 4.dp),
                    )
                    Text(
                        text = stringResource(category.labelRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.places_show_visited),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Switch(
                    checked = showVisited,
                    onCheckedChange = onShowVisitedChange,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EmptyState(hasFilters: Boolean, searchQuery: String, onAddPlace: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (hasFilters) stringResource(R.string.places_empty_filtered_title) else stringResource(R.string.places_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (hasFilters) stringResource(R.string.places_empty_filtered_subtitle) else stringResource(R.string.places_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (searchQuery.isNotBlank()) {
                androidx.compose.material3.Button(
                    onClick = { onAddPlace(searchQuery) },
                    modifier = Modifier.padding(top = 20.dp),
                ) {
                    Text(stringResource(R.string.places_search_add_new, searchQuery))
                }
            }
        }
    }
}
