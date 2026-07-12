package com.brbrs.qarib.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.brbrs.qarib.R
import com.brbrs.qarib.ui.theme.GEOFENCE_RADIUS_OPTIONS
import com.brbrs.qarib.ui.theme.qaribChip

/**
 * Horizontally scrollable row of radius chips (100m through 5km).
 * [selected] is the currently chosen radius in meters; pass null to
 * highlight nothing (e.g. when a separate "Default" chip is selected).
 */
@Composable
fun RadiusOptionRow(
    selected: Int?,
    onSelect: (Int) -> Unit,
    isDark: Boolean,
    extraOption: @Composable (() -> Unit)? = null,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 4.dp),
    ) {
        if (extraOption != null) {
            item { extraOption() }
        }
        items(GEOFENCE_RADIUS_OPTIONS) { radius ->
            val isSelected = selected == radius
            Box(
                modifier = Modifier
                    .qaribChip(isDark = isDark, selected = isSelected)
                    .clickable { onSelect(radius) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = radiusLabel(radius),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun radiusLabel(radiusMeters: Int): String = when (radiusMeters) {
    100 -> stringResource(R.string.settings_geofence_radius_100)
    250 -> stringResource(R.string.settings_geofence_radius_250)
    500 -> stringResource(R.string.settings_geofence_radius_500)
    1000 -> stringResource(R.string.settings_geofence_radius_1000)
    2000 -> stringResource(R.string.settings_geofence_radius_2000)
    5000 -> stringResource(R.string.settings_geofence_radius_5000)
    else -> if (radiusMeters >= 1000) "${radiusMeters / 1000} km" else "$radiusMeters m"
}
