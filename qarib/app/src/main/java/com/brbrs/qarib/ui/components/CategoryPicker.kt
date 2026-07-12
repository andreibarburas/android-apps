package com.brbrs.qarib.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.brbrs.qarib.ui.theme.PlaceCategory
import com.brbrs.qarib.ui.theme.categoryColor
import com.brbrs.qarib.ui.theme.icon
import com.brbrs.qarib.ui.theme.labelRes

@Composable
fun CategoryPicker(
    selected: PlaceCategory,
    onSelect: (PlaceCategory) -> Unit
) {
    LazyRow(
        modifier = Modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 4.dp),
    ) {
        items(PlaceCategory.entries.toList()) { category ->
            val isSelected = category == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(category) },
                label = { Text(stringResource(category.labelRes())) },
                leadingIcon = {
                    Icon(
                        imageVector = category.icon(),
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else categoryColor(category)
                    )
                }
            )
        }
    }
}
