package com.brbrs.qarib.data.remote

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * Minimal parser for GPX waypoints (<wpt> elements), the common format
 * used by "saved places" exports from Google Maps, OsmAnd, and similar
 * apps.
 *
 * Example element:
 * <wpt lat="52.3676" lon="4.9041">
 *   <name>Anne Frank House</name>
 *   <desc>Museum</desc>
 * </wpt>
 */
data class GpxWaypoint(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val description: String,
)

object GpxParser {

    /** Parses all <wpt> elements from [input]. Returns an empty list on malformed input. */
    fun parseWaypoints(input: InputStream): List<GpxWaypoint> {
        val waypoints = mutableListOf<GpxWaypoint>()

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var eventType = parser.eventType
        var inWaypoint = false
        var lat: Double? = null
        var lon: Double? = null
        var name = ""
        var desc = ""
        var currentTag = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name
                    when (tag) {
                        "wpt" -> {
                            inWaypoint = true
                            lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                            lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                            name = ""
                            desc = ""
                        }
                        "name", "desc", "cmt" -> if (inWaypoint) currentTag = tag
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inWaypoint) {
                        val text = parser.text?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            when (currentTag) {
                                "name" -> name = text
                                "desc", "cmt" -> if (desc.isEmpty()) desc = text
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "wpt" -> {
                            val latitude = lat
                            val longitude = lon
                            if (latitude != null && longitude != null) {
                                waypoints.add(
                                    GpxWaypoint(
                                        name = name.ifBlank { "Imported place" },
                                        latitude = latitude,
                                        longitude = longitude,
                                        description = desc,
                                    )
                                )
                            }
                            inWaypoint = false
                            currentTag = ""
                        }
                        "name", "desc", "cmt" -> currentTag = ""
                    }
                }
            }
            eventType = parser.next()
        }

        return waypoints
    }
}
