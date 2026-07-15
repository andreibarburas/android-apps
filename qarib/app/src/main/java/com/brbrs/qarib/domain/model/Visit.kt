package com.brbrs.qarib.domain.model

import com.brbrs.qarib.data.local.entity.VisitEntity
import org.json.JSONArray
import java.util.UUID

/**
 * UI/domain representation of a single recorded visit to a place.
 * [photoPaths] is the deserialized list of local photo file paths.
 */
data class Visit(
    val id: String,
    val placeId: String,
    val visitedAt: Long,
    val note: String,
    val photoPaths: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
)

fun VisitEntity.toDomain(): Visit = Visit(
    id = id,
    placeId = placeId,
    visitedAt = visitedAt,
    note = note,
    photoPaths = parsePhotoPaths(photoPaths),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Visit.toEntity(): VisitEntity = VisitEntity(
    id = id,
    placeId = placeId,
    visitedAt = visitedAt,
    note = note,
    photoPaths = serializePhotoPaths(photoPaths),
    createdAt = createdAt,
    updatedAt = updatedAt,
    deleted = false,
)

fun newVisit(placeId: String, visitedAt: Long = System.currentTimeMillis()): Visit {
    val now = System.currentTimeMillis()
    return Visit(
        id = UUID.randomUUID().toString(),
        placeId = placeId,
        visitedAt = visitedAt,
        note = "",
        photoPaths = emptyList(),
        createdAt = now,
        updatedAt = now,
    )
}

fun parsePhotoPaths(json: String): List<String> = try {
    val arr = JSONArray(json)
    List(arr.length()) { arr.getString(it) }.filter { it.isNotBlank() }
} catch (e: Exception) {
    emptyList()
}

fun serializePhotoPaths(paths: List<String>): String {
    val arr = JSONArray()
    paths.forEach { arr.put(it) }
    return arr.toString()
}
