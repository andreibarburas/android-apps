package com.brbrs.qarib.ui.screens.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.qarib.R
import com.brbrs.qarib.domain.model.GeocodeResult
import com.brbrs.qarib.ui.components.CategoryPicker
import com.brbrs.qarib.ui.components.PhotoPickerRow
import com.brbrs.qarib.ui.components.RadiusOptionRow
import com.brbrs.qarib.ui.components.radiusLabel
import com.brbrs.qarib.ui.theme.LocalIsDark
import com.brbrs.qarib.ui.theme.qaribBackground
import com.brbrs.qarib.ui.theme.qaribChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlaceScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditPlaceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = LocalIsDark.current

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_place_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .qaribBackground(isDark)
        ) {
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.edit_place_name_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )

            Text(
                text = stringResource(R.string.add_place_category_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            )
            CategoryPicker(
                selected = uiState.category,
                onSelect = viewModel::onCategorySelected
            )

            Text(
                text = stringResource(R.string.photo_picker_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            )
            PhotoPickerRow(
                photoPath = uiState.photoPath,
                onPhotoPicked = viewModel::onPhotoPicked,
                onCreateCameraCapture = viewModel::createCameraCaptureTarget,
                onCameraCaptureComplete = viewModel::onCameraCaptureComplete,
                onRemovePhoto = viewModel::removePhoto,
            )

            TextField(
                value = uiState.note,
                onValueChange = viewModel::onNoteChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                placeholder = { Text(stringResource(R.string.add_place_note_placeholder)) },
                minLines = 2,
                maxLines = 4
            )

            Text(
                text = stringResource(R.string.edit_place_radius_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            )
            RadiusOptionRow(
                selected = uiState.geofenceRadiusMeters,
                onSelect = { viewModel.onGeofenceRadiusChange(it) },
                isDark = isDark,
                extraOption = {
                    val isSelected = uiState.geofenceRadiusMeters == null
                    Box(
                        modifier = Modifier
                            .qaribChip(isDark = isDark, selected = isSelected)
                            .clickable { viewModel.onGeofenceRadiusChange(null) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.edit_place_radius_default, radiusLabel(uiState.defaultRadiusMeters)),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            )

            Text(
                text = stringResource(R.string.edit_place_location_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            )

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = uiState.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(12.dp)
                )
            }

            com.brbrs.qarib.ui.screens.map.PlacePreviewMap(
                latitude = uiState.latitude,
                longitude = uiState.longitude,
                accentColor = com.brbrs.qarib.ui.theme.categoryColor(uiState.category),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.locationQuery,
                    onValueChange = viewModel::onLocationQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    placeholder = { Text(stringResource(R.string.add_place_search_placeholder)) },
                    singleLine = true
                )
                Box(modifier = Modifier.padding(start = 8.dp)) {
                    Button(onClick = { viewModel.searchLocation() }, enabled = !uiState.isSearching) {
                        if (uiState.isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.add_place_search_button))
                        }
                    }
                }
            }

            if (uiState.searchError == "no_results") {
                Text(
                    text = stringResource(R.string.add_place_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else if (uiState.searchError != null) {
                Text(
                    text = uiState.searchError ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            } // end scrollable form column

            if (uiState.searchResults.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.searchResults) { result ->
                        SearchResultRow(result = result, onClick = { viewModel.selectLocation(result) })
                    }
                }
            }

            Button(
                onClick = { viewModel.save() },
                enabled = uiState.name.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Text(stringResource(R.string.edit_place_save_button))
            }
        }
        } // end qaribBackground Box
    }
}

@Composable
private fun SearchResultRow(result: GeocodeResult, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = result.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(12.dp)
        )
    }
}
