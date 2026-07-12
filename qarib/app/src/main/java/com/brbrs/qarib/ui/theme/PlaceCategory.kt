package com.brbrs.qarib.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.ui.graphics.vector.ImageVector
import com.brbrs.qarib.R

/**
 * Category tags for saved places. The string value is the stable
 * identifier persisted to Room and the Nextcloud JSON sync file —
 * do not rename these without a migration.
 */
enum class PlaceCategory(val storageKey: String) {
    RESTAURANT("restaurant"),
    CAFE("cafe"),
    BAR("bar"),
    HOTEL("hotel"),
    ATTRACTION("attraction"),
    MUSEUM("museum"),
    PARK("park"),
    ACTIVITY("activity"),
    SHOP("shop");

    companion object {
        fun fromStorageKey(key: String): PlaceCategory =
            entries.firstOrNull { it.storageKey == key } ?: RESTAURANT
    }
}

fun PlaceCategory.labelRes(): Int = when (this) {
    PlaceCategory.RESTAURANT -> R.string.category_restaurant
    PlaceCategory.CAFE -> R.string.category_cafe
    PlaceCategory.BAR -> R.string.category_bar
    PlaceCategory.HOTEL -> R.string.category_hotel
    PlaceCategory.ATTRACTION -> R.string.category_attraction
    PlaceCategory.MUSEUM -> R.string.category_museum
    PlaceCategory.PARK -> R.string.category_park
    PlaceCategory.ACTIVITY -> R.string.category_activity
    PlaceCategory.SHOP -> R.string.category_shop
}

fun PlaceCategory.icon(): ImageVector = when (this) {
    PlaceCategory.RESTAURANT -> Icons.Outlined.Restaurant
    PlaceCategory.CAFE -> Icons.Outlined.LocalCafe
    PlaceCategory.BAR -> Icons.Outlined.LocalBar
    PlaceCategory.HOTEL -> Icons.Outlined.Hotel
    PlaceCategory.ATTRACTION -> Icons.Outlined.Camera
    PlaceCategory.MUSEUM -> Icons.Outlined.AccountBalance
    PlaceCategory.PARK -> Icons.Outlined.Park
    PlaceCategory.ACTIVITY -> Icons.Outlined.DirectionsWalk
    PlaceCategory.SHOP -> Icons.Outlined.ShoppingBag
}
