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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(place: PlaceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(places: List<PlaceEntity>)

    @Update
    suspend fun update(place: PlaceEntity)

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

    /** Suppresses "nearby" notifications for [id] until [until] (epoch millis). */
    @Query("UPDATE places SET snoozedUntil = :until WHERE id = :id")
    suspend fun setSnoozedUntil(id: String, until: Long?)

    /** Suppresses "nearby" notifications for [id] until the user exits its geofence. */
    @Query("UPDATE places SET snoozedUntilExit = :snoozed WHERE id = :id")
    suspend fun setSnoozedUntilExit(id: String, snoozed: Boolean)

    @Query("UPDATE places SET deleted = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun markDeleted(id: String, timestamp: Long)

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM places WHERE deleted = 1")
    suspend fun purgeDeleted()
}
