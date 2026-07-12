package com.brbrs.nota.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val content: String,
    val category: String,           // Nextcloud Notes uses a single category string
    val modified: Long,             // Unix timestamp (seconds)
    val favorite: Boolean = false,
    val readonly: Boolean = false,
    val etag: String = "",
    val isLocked: Boolean = false,  // per-note biometric lock (local only)
    val isDirty: Boolean = false,   // has local changes not yet synced
    val isDeleted: Boolean = false, // soft-delete pending sync
)
