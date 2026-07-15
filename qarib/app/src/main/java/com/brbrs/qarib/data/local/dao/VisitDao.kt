package com.brbrs.qarib.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.brbrs.qarib.data.local.entity.VisitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitDao {

    @Query("SELECT * FROM visits WHERE placeId = :placeId AND deleted = 0 ORDER BY visitedAt DESC")
    fun observeByPlace(placeId: String): Flow<List<VisitEntity>>

    @Query("SELECT * FROM visits WHERE placeId = :placeId AND deleted = 0 ORDER BY visitedAt DESC")
    suspend fun getByPlace(placeId: String): List<VisitEntity>

    @Query("SELECT * FROM visits WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VisitEntity?

    @Query("SELECT * FROM visits")
    suspend fun getAllIncludingDeleted(): List<VisitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(visit: VisitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(visits: List<VisitEntity>)

    @Query("UPDATE visits SET deleted = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun markDeleted(id: String, timestamp: Long)

    @Query("DELETE FROM visits WHERE deleted = 1")
    suspend fun purgeDeleted()

    /** Returns the most recent non-deleted visit for a place, or null. */
    @Query("SELECT * FROM visits WHERE placeId = :placeId AND deleted = 0 ORDER BY visitedAt DESC LIMIT 1")
    suspend fun getLatestByPlace(placeId: String): VisitEntity?
}
