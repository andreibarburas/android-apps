package com.brbrs.qarib.ui.screens.map

import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.brbrs.qarib.domain.model.Place
import com.brbrs.qarib.ui.theme.LocalIsDark
import com.brbrs.qarib.ui.theme.categoryColor
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * CartoDB Positron — muted, minimal-label tiles that sit better with
 * Qarib's warm rose/sage palette than the default OSM Mapnik style.
 * Free, no API key required.
 */
private val CartoPositron: ITileSource = XYTileSource(
    "CartoDBPositron",
    0, 20, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/light_all/",
        "https://b.basemaps.cartocdn.com/light_all/",
        "https://c.basemaps.cartocdn.com/light_all/",
    ),
    "© OpenStreetMap contributors © CARTO"
)

/**
 * CartoDB Dark Matter — dark-mode counterpart to [CartoPositron].
 */
private val CartoDarkMatter: ITileSource = XYTileSource(
    "CartoDBDarkMatter",
    0, 20, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
    ),
    "© OpenStreetMap contributors © CARTO"
)

/**
 * Map view backed by osmdroid (OpenStreetMap tiles) — no Google API key
 * required. Shows a pin per saved place, colored by category.
 */
@Composable
fun OsmMapView(
    places: List<Place>,
    onMarkerClick: (Place) -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDark = LocalIsDark.current
    val tileSource = if (isDark) CartoDarkMatter else CartoPositron

    // categoryColor() is @Composable, so resolve all marker colors here,
    // in a composable context, before passing them into AndroidView's
    // factory/update lambdas (which are not composable contexts).
    val markerArgbByPlaceId: Map<String, Int> = places.associate { place ->
        place.id to categoryColor(place.category).toArgb()
    }

    // Only fit the map to all places once, on first load — afterwards the
    // user's own pan/zoom should be respected even as `places` changes
    // (e.g. after a sync or filter change).
    val hasFitBounds = remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
        factory = { ctx ->
            Configuration.getInstance().userAgentValue = "Qarib-Android-App/0.1"

            MapView(ctx).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setTileSource(tileSource)
                setMultiTouchControls(true)

                // Default view before the first layout pass / fit-to-bounds.
                controller.setZoom(12.0)
                val fallbackCenter = if (places.isNotEmpty()) {
                    GeoPoint(places.first().latitude, places.first().longitude)
                } else {
                    // Default to Amsterdam if there are no places yet.
                    GeoPoint(52.3676, 4.9041)
                }
                controller.setCenter(fallbackCenter)
            }
        },
        update = { mapView ->
            if (mapView.tileProvider.tileSource.name() != tileSource.name()) {
                mapView.setTileSource(tileSource)
            }

            mapView.overlays.clear()

            for (place in places) {
                val marker = Marker(mapView)
                marker.position = GeoPoint(place.latitude, place.longitude)
                marker.title = place.name
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                val argb = markerArgbByPlaceId[place.id] ?: AndroidColor.GRAY
                marker.icon = createMarkerDrawable(argb)
                marker.setOnMarkerClickListener { _, _ ->
                    onMarkerClick(place)
                    true
                }
                mapView.overlays.add(marker)
            }
            mapView.invalidate()

            if (!hasFitBounds.value && places.isNotEmpty()) {
                hasFitBounds.value = true
                fitToPlaces(mapView, places)
            }
        }
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // osmdroid recommends pausing/resuming tile downloads with the
            // lifecycle; handled implicitly by the AndroidView lifecycle.
            when (event) {
                Lifecycle.Event.ON_PAUSE -> Unit
                Lifecycle.Event.ON_RESUME -> Unit
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

/**
 * Centers and zooms [mapView] to show all of [places].
 *
 * For a single place, centers on it at a reasonable street-level zoom
 * (a bounding box of one point has zero area, which
 * [MapView.zoomToBoundingBox] handles poorly). For multiple places,
 * fits the bounding box of all of them with padding, deferring to the
 * map's first layout pass if its size isn't known yet (zoomToBoundingBox
 * needs a non-zero view size to compute the right zoom level).
 */
private fun fitToPlaces(mapView: MapView, places: List<Place>) {
    if (places.size == 1) {
        val place = places.first()
        mapView.controller.setZoom(14.0)
        mapView.controller.setCenter(GeoPoint(place.latitude, place.longitude))
        return
    }

    val latitudes = places.map { it.latitude }
    val longitudes = places.map { it.longitude }
    val boundingBox = org.osmdroid.util.BoundingBox(
        latitudes.max(),
        longitudes.max(),
        latitudes.min(),
        longitudes.min(),
    )

    if (mapView.width > 0 && mapView.height > 0) {
        mapView.zoomToBoundingBox(boundingBox, false, 64)
    } else {
        // View hasn't been laid out yet — retry once it has a size.
        mapView.addOnFirstLayoutListener { view, _, _, _, _ ->
            (view as MapView).zoomToBoundingBox(boundingBox, false, 64)
        }
    }
}

/**
 * Builds a simple circular pin drawable tinted with the given ARGB color.
 */
private fun createMarkerDrawable(color: Int): android.graphics.drawable.Drawable {
    val size = 64
    val drawable = GradientDrawable()
    drawable.shape = GradientDrawable.OVAL
    drawable.setColor(color)
    drawable.setStroke(4, AndroidColor.WHITE)
    drawable.setSize(size, size)
    drawable.setBounds(0, 0, size, size)
    return drawable
}

/**
 * Small, read-only map preview centered on a single point, used in the
 * Add/Edit place screens so the user can confirm where a place will be
 * (or is) located. Pan/zoom are disabled to avoid gesture conflicts with
 * the surrounding scrollable form — re-centers automatically if
 * [latitude]/[longitude] change (e.g. after re-searching a location).
 */
@Composable
fun PlacePreviewMap(
    latitude: Double,
    longitude: Double,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val tileSource = if (isDark) CartoDarkMatter else CartoPositron
    val markerArgb = accentColor.toArgb()

    AndroidView(
        modifier = modifier.clipToBounds(),
        factory = { ctx ->
            Configuration.getInstance().userAgentValue = "Qarib-Android-App/0.1"

            MapView(ctx).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setTileSource(tileSource)
                setMultiTouchControls(false)
                setBuiltInZoomControls(false)
                setOnTouchListener { _, _ -> true } // read-only: swallow touches, no pan/zoom
                controller.setZoom(15.0)
                controller.setCenter(GeoPoint(latitude, longitude))

                val marker = Marker(this)
                marker.position = GeoPoint(latitude, longitude)
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                marker.icon = createMarkerDrawable(markerArgb)
                overlays.add(marker)
            }
        },
        update = { mapView ->
            if (mapView.tileProvider.tileSource.name() != tileSource.name()) {
                mapView.setTileSource(tileSource)
            }

            mapView.controller.setCenter(GeoPoint(latitude, longitude))

            mapView.overlays.clear()
            val marker = Marker(mapView)
            marker.position = GeoPoint(latitude, longitude)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.icon = createMarkerDrawable(markerArgb)
            mapView.overlays.add(marker)
            mapView.invalidate()
        }
    )
}
