package com.brbrs.qarib.data.repository

import com.brbrs.qarib.auth.AuthRepository
import com.brbrs.qarib.data.local.PhotoStorage
import com.brbrs.qarib.data.local.dao.PlaceDao
import com.brbrs.qarib.data.local.entity.PlaceEntity
import com.brbrs.qarib.data.remote.NextcloudWebDavClient
import com.brbrs.qarib.data.remote.PlacesJsonSerializer
import com.brbrs.qarib.domain.model.Place
import com.brbrs.qarib.domain.model.deriveCountryFromAddress
import com.brbrs.qarib.domain.model.toDomain
import com.brbrs.qarib.domain.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncResult {
    object Success : SyncResult()
    object NotConnected : SyncResult()
    data class Error(val message: String) : SyncResult()
}

@Singleton
class PlacesRepository @Inject constructor(
    private val placeDao: PlaceDao,
    private val webDavClient: NextcloudWebDavClient,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val photoStorage: PhotoStorage,
) {
    val places: Flow<List<Place>> = placeDao.observeAll().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun savePlace(place: Place) {
        placeDao.upsert(place.toEntity())
    }

    /** Updates an existing place's editable fields, preserving timestamps/flags as given. */
    suspend fun updatePlace(place: Place) {
        placeDao.update(place.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun setVisited(id: String, visited: Boolean) {
        placeDao.setVisited(id, visited, System.currentTimeMillis())
    }

    suspend fun setNotificationsMuted(id: String, muted: Boolean) {
        placeDao.setNotificationsMuted(id, muted, System.currentTimeMillis())
    }

    /**
     * Fills in `country` for any local places saved before schema v2,
     * deriving it from the stored `address` string. Safe to call
     * repeatedly — only touches rows where `country` is still empty.
     */
    suspend fun backfillCountries() {
        val toUpdate = placeDao.getPlacesWithEmptyCountry()
        for (entity in toUpdate) {
            val country = deriveCountryFromAddress(entity.address)
            if (country.isNotEmpty()) {
                placeDao.setCountry(entity.id, country)
            }
        }
    }

    suspend fun deletePlace(id: String) {
        val entity = placeDao.getById(id)
        if (entity != null && entity.photoPath.isNotEmpty()) {
            photoStorage.deletePhoto(entity.photoPath)
        }
        placeDao.markDeleted(id, System.currentTimeMillis())
    }

    /**
     * Saves a batch of imported places (e.g. from a GPX file). Returns the
     * number of places actually inserted.
     */
    suspend fun importPlaces(places: List<Place>): Int {
        if (places.isEmpty()) return 0
        placeDao.upsertAll(places.map { it.toEntity() })
        return places.size
    }

    /**
     * Performs a two-way sync with the Qarib app folder on Nextcloud:
     * downloads the remote places.json (if any), merges with local
     * changes using last-write-wins on [PlaceEntity.updatedAt], syncs
     * any attached photos to/from Qarib/photos/, then uploads the merged
     * result.
     *
     * Returns [SyncResult.NotConnected] if no Nextcloud account is
     * configured.
     */
    suspend fun sync(): SyncResult {
        val session = authRepository.session.first() ?: return SyncResult.NotConnected

        // Ensure the app folder exists.
        val folderResult = webDavClient.ensureAppFolder(session)
        if (folderResult is NextcloudWebDavClient.Result.Error) {
            return SyncResult.Error(folderResult.message)
        }

        // Download remote state.
        val remoteJson = webDavClient.downloadJson(session, NextcloudWebDavClient.PLACES_FILE)
        val remoteJsonString: String
        val remotePlaces: List<PlaceEntity>
        val localPhotoPaths = placeDao.getAllIncludingDeleted().associate { it.id to it.photoPath }

        when (remoteJson) {
            is NextcloudWebDavClient.Result.Success -> {
                remoteJsonString = remoteJson.data
                remotePlaces = PlacesJsonSerializer.deserialize(remoteJson.data, localPhotoPaths)
            }
            is NextcloudWebDavClient.Result.NotFound -> {
                remoteJsonString = ""
                remotePlaces = emptyList()
            }
            is NextcloudWebDavClient.Result.Error -> return SyncResult.Error(remoteJson.message)
        }
        val remoteHasPhoto = PlacesJsonSerializer.readHasPhotoFlags(remoteJsonString)

        // Merge with local state using last-write-wins on updatedAt.
        val localPlaces = placeDao.getAllIncludingDeleted()
        var merged = mergeByLastWrite(localPlaces, remotePlaces)

        // Sync photos: upload local photos that are new/changed, download
        // remote photos this device doesn't have yet.
        merged = syncPhotos(session, merged, remoteHasPhoto)

        // Persist merged result locally, then purge old tombstones.
        placeDao.upsertAll(merged)
        placeDao.purgeDeleted()

        // Upload merged result, including deleted flags as tombstones so
        // other devices can pick up deletions on their next sync.
        val json = PlacesJsonSerializer.serialize(merged)
        val uploadResult = webDavClient.uploadJson(session, NextcloudWebDavClient.PLACES_FILE, json)
        if (uploadResult is NextcloudWebDavClient.Result.Error) {
            return SyncResult.Error(uploadResult.message)
        }

        settingsRepository.setLastSyncAt(System.currentTimeMillis())
        return SyncResult.Success
    }

    /**
     * Uploads local photos that aren't reflected remotely yet, and
     * downloads remote photos this device doesn't have locally. Remote
     * photos are named "{place.id}.jpg" so all devices converge on one
     * canonical file per place. Returns [merged] with [PlaceEntity.photoPath]
     * updated for any newly downloaded photos.
     */
    private suspend fun syncPhotos(
        session: com.brbrs.qarib.auth.QaribSession,
        merged: List<PlaceEntity>,
        remoteHasPhoto: Map<String, Boolean>,
    ): List<PlaceEntity> {
        var photosFolderEnsured = false

        return merged.map { entity ->
            if (entity.deleted) return@map entity

            val remoteHas = remoteHasPhoto[entity.id] == true
            val localHas = entity.photoPath.isNotEmpty()

            when {
                localHas -> {
                    // Upload local photo if remote doesn't have one, or
                    // this place was updated more recently than the last
                    // sync would suggest (we always re-upload on change
                    // since we don't track a separate photo timestamp;
                    // uploading an unchanged file is cheap and idempotent).
                    val bytes = photoStorage.readPhotoBytes(entity.photoPath)
                    if (bytes != null) {
                        if (!photosFolderEnsured) {
                            webDavClient.ensurePhotosFolder(session)
                            photosFolderEnsured = true
                        }
                        webDavClient.uploadBytes(session, "${entity.id}.jpg", bytes)
                    }
                    entity
                }
                remoteHas -> {
                    // Download the remote photo since this device doesn't
                    // have it yet.
                    when (val result = webDavClient.downloadBytes(session, "${entity.id}.jpg")) {
                        is NextcloudWebDavClient.Result.Success -> {
                            val path = photoStorage.savePhotoBytes(result.data, entity.id)
                            if (path != null) entity.copy(photoPath = path) else entity
                        }
                        else -> entity
                    }
                }
                else -> entity
            }
        }
    }

    private fun mergeByLastWrite(local: List<PlaceEntity>, remote: List<PlaceEntity>): List<PlaceEntity> {
        val byId = mutableMapOf<String, PlaceEntity>()
        for (entity in local) byId[entity.id] = entity
        for (entity in remote) {
            val existing = byId[entity.id]
            if (existing == null || entity.updatedAt > existing.updatedAt) {
                byId[entity.id] = entity
            }
        }
        return byId.values.toList()
    }
}
