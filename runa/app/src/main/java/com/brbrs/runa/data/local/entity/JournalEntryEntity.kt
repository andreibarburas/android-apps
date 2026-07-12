package com.brbrs.runa.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val body: String,
    val entryDateTimeMs: Long,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val photoPathsJson: String = "[]",
    val isSynced: Boolean = false,
    val deleted: Boolean  = false,
    // v2 — location
    val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
