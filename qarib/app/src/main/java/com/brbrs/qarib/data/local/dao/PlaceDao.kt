package com.brbrs.qarib.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.brbrs.qarib.data.local.entity.PlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {

    @Query("SELECT * FROM places WHERE deleted = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PlaceEntity?

    @Query("SELECT * FROM places")
    suspend fun getAllIncludingDeleted(): List<PlaceEntity>

    /**
     * Safe upsert: INSERT-or-IGNORE (never deletes the row, so ON DELETE CASCADE
     * on child visits is never triggered), followed by UPDATE for existing rows.
     *
     * DO NOT use OnConflictStrategy.REPLACE here — SQLite implements REPLACE as
     * DELETE + INSERT, which fires the foreign-key CASCADE and wipes all visits
     * for the place being upserted.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(place: PlaceEntity): Long

    @Update
    suspend fun update(place: PlaceEntity)

    suspend fun upsert(place: PlaceEntity) {
        if (insertIgnore(place) == -1L) update(place)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(places: List<PlaceEntity>): List<Long>

    suspend fun upsertAll(places: List<PlaceEntity>) {
        val results = insertAllIgnore(places)
        val toUpdate = places.zip(results).filter { (_, id) -> id == -1L }.map { it.first }
        if (toUpdate.isNotEmpty()) updateAll(toUpdate)
    }

    @Update
    suspend fun updateAll(places: List<PlaceEntity>)

    @Query("SELECT * FROM places WHERE country = '' AND deleted = 0")
    suspend fun getPlacesWithEmptyCountry(): List<PlaceEntity>

    @Query("UPDATE places SET country = :country WHERE id = :id")
    suspend fun setCountry(id: String, country: String)

    @Query("UPDATE places SET photoPath = :photoPath WHERE id = :id")
    suspend fun setPhotoPath(id: String, photoPath: String)

    @Query("UPDATE places SET visited = :visited, updatedAt = :timestamp WHERE id = :id")
    suspend fun setVisited(id: String, visited: Boolean, timestamp: Long)

    @Query("UPDATE places SET notificationsMuted = :muted, updatedAt = :timestamp WHERE id = :id")
    suspend fun setNotificationsMuted(id: String, muted: Boolean, timestamp: Long)

    @Query("UPDATE places SET snoozedUntil = :until WHERE id = :id")
    suspend fun setSnoozedUntil(id: String, until: Long?)

    @Query("UPDATE places SET snoozedUntilExit = :snoozed WHERE id = :id")
    suspend fun setSnoozedUntilExit(id: String, snoozed: Boolean)

    @Query("UPDATE places SET deleted = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun markDeleted(id: String, timestamp: Long)

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM places WHERE deleted = 1")
    suspend fun purgeDeleted()
}
