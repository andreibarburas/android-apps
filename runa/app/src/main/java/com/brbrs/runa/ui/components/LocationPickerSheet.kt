package com.brbrs.runa.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.brbrs.runa.data.repository.LocationRepository
import com.brbrs.runa.data.repository.LocationResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun LocationPickerSheet(
    locationRepository: LocationRepository,
    onLocationSelected: (LocationResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope          = rememberCoroutineScope()
    var query          by remember { mutableStateOf("") }
    var results        by remember { mutableStateOf<List<LocationResult>>(emptyList()) }
    var isSearching    by remember { mutableStateOf(false) }
    val queryFlow      = remember { MutableStateFlow("") }
    val focusRequester = remember { FocusRequester() }

    // Debounced search
    LaunchedEffect(Unit) {
        queryFlow
            .debounce(400)
            .distinctUntilChanged()
            .collect { q ->
                if (q.length >= 2) {
                    isSearching = true
                    results     = locationRepository.search(q)
                    isSearching = false
                } else {
                    results = emptyList()
                }
            }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        sheetState        = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor    = MaterialTheme.colorScheme.surface,
        dragHandle        = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                "Add location",
                style    = MaterialTheme.typography.titleMedium,
                color    = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            OutlinedTextField(
                value         = query,
                onValueChange = { q ->
                    query = q
                    scope.launch { queryFlow.emit(q) }
                },
                placeholder   = { Text("Search places…") },
                leadingIcon   = {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                },
                trailingIcon  = if (query.isNotBlank()) ({
                    IconButton(onClick = { query = ""; results = emptyList(); scope.launch { queryFlow.emit("") } }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear")
                    }
                }) else null,
                singleLine    = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier      = Modifier.fillMaxWidth().focusRequester(focusRequester),
                shape         = RoundedCornerShape(14.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor          = MaterialTheme.colorScheme.primary,
                ),
            )

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier       = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(results, key = { "${it.latitude}-${it.longitude}" }) { result ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLocationSelected(result); onDismiss() }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text     = result.shortName,
                                style    = MaterialTheme.typography.bodyMedium,
                                color    = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                            Text(
                                text     = result.displayName,
                                style    = MaterialTheme.typography.labelSmall,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                }
            }
        }
    }
}
