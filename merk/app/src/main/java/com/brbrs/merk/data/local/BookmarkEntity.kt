package com.brbrs.merk.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: Long,
    val url: String,
    val title: String,
    val description: String,
    val tags: String,           // comma-separated
    val folderId: Long,
    val folderName: String,
    val faviconUrl: String,
    val addedAt: Long,
    // sync flags
    val isDirty: Boolean = false,
    val isPendingDelete: Boolean = false,
    val localId: Long = 0,      // negative = local-only (not yet synced)
)
