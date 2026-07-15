package com.brbrs.qarib.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A single recorded visit to a saved place.
 *
 * [photoPaths] is a JSON array of absolute local file paths, e.g.
 * `["/.../photos/visit-abc-123.jpg", "..."]`. Empty array = no photos.
 * Synced to Nextcloud as individual files under Qarib/visit-photos/.
 */
@Entity(
    tableName = "visits",
    foreignKeys = [
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("placeId")]
)
data class VisitEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val placeId: String,
    /** Epoch millis when the visit was recorded. */
    val visitedAt: Long,
    /** Optional user note about this visit. */
    val note: String = "",
    /** JSON array of absolute local photo file paths for this visit. */
    val photoPaths: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)
