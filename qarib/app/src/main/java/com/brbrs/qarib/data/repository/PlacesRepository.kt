package com.brbrs.qarib.data.repository

import com.brbrs.qarib.auth.AuthRepository
import com.brbrs.qarib.data.local.PhotoStorage
import com.brbrs.qarib.data.local.dao.PlaceDao
import com.brbrs.qarib.data.local.dao.VisitDao
import com.brbrs.qarib.data.local.entity.PlaceEntity
import com.brbrs.qarib.data.local.entity.VisitEntity
import com.brbrs.qarib.data.remote.NextcloudWebDavClient
import com.brbrs.qarib.data.remote.PlacesJsonSerializer
import com.brbrs.qarib.domain.model.Place
import com.brbrs.qarib.domain.model.Visit
import com.brbrs.qarib.domain.model.deriveCountryFromAddress
import com.brbrs.qarib.domain.model.parsePhotoPaths
import com.brbrs.qarib.domain.model.serializePhotoPaths
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
    private val visitDao: VisitDao,
    private val webDavClient: NextcloudWebDavClient,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val photoStorage: PhotoStorage,
) {
    val places: Flow<List<Place>> = placeDao.observeAll().map { entities ->
        entities.map { it.toDomain() }
    }

    // ── Visit history ─────────────────────────────────────────────────────────

    fun visitsForPlace(placeId: String): Flow<List<Visit>> =
        visitDao.observeByPlace(placeId).map { entities -> entities.map { it.toDomain() } }

    suspend fun saveVisit(visit: Visit) {
        visitDao.upsert(visit.toEntity())
        placeDao.setVisited(visit.placeId, true, System.currentTimeMillis())
    }

    suspend fun deleteVisit(visitId: String) {
        val entity = visitDao.getById(visitId) ?: return
        parsePhotoPaths(entity.photoPaths).forEach { path ->
            photoStorage.deletePhoto(path)
        }
        visitDao.markDeleted(visitId, System.currentTimeMillis())
    }

    suspend fun getLatestVisit(placeId: String): Visit? =
        visitDao.getLatestByPlace(placeId)?.toDomain()

    // ── Place CRUD ────────────────────────────────────────────────────────────

    suspend fun savePlace(place: Place) {
        placeDao.upsert(place.toEntity())
    }

    suspend fun updatePlace(place: Place) {
        placeDao.update(place.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun setVisited(id: String, visited: Boolean) {
        placeDao.setVisited(id, visited, System.currentTimeMillis())
    }

    suspend fun setNotificationsMuted(id: String, muted: Boolean) {
        placeDao.setNotificationsMuted(id, muted, System.currentTimeMillis())
    }

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

    suspend fun importPlaces(places: List<Place>): Int {
        if (places.isEmpty()) return 0
        placeDao.upsertAll(places.map { it.toEntity() })
        return places.size
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    suspend fun sync(): SyncResult {
        val session = authRepository.session.first() ?: return SyncResult.NotConnected

        val folderResult = webDavClient.ensureAppFolder(session)
        if (folderResult is NextcloudWebDavClient.Result.Error) {
            return SyncResult.Error(folderResult.message)
        }

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

        // Merge places.
        val localPlaces = placeDao.getAllIncludingDeleted()
        var merged = mergeByLastWrite(localPlaces, remotePlaces)
        merged = syncPhotos(session, merged, remoteHasPhoto)
        placeDao.upsertAll(merged)
        placeDao.purgeDeleted()

        // Merge visits.
        val localVisits = visitDao.getAllIncludingDeleted()
        val localVisitPhotoPaths = localVisits.associate { it.id to parsePhotoPaths(it.photoPaths) }
        val remoteVisitPhotoCount = PlacesJsonSerializer.readVisitPhotoFlags(remoteJsonString)
        val remoteVisits = PlacesJsonSerializer.deserializeVisits(remoteJsonString, localVisitPhotoPaths)
        android.util.Log.d("PlacesRepo", "sync: remoteVisits count=${remoteVisits.size}")
        var mergedVisits = mergeVisitsByLastWrite(localVisits, remoteVisits)
        mergedVisits = syncVisitPhotos(session, mergedVisits, remoteVisitPhotoCount)
        visitDao.upsertAll(mergedVisits)
        visitDao.purgeDeleted()

        // Upload merged result.
        val visitsByPlaceId = mergedVisits.groupBy { it.placeId }
        val json = PlacesJsonSerializer.serialize(merged, visitsByPlaceId)
        val uploadResult = webDavClient.uploadJson(session, NextcloudWebDavClient.PLACES_FILE, json)
        if (uploadResult is NextcloudWebDavClient.Result.Error) {
            return SyncResult.Error(uploadResult.message)
        }

        settingsRepository.setLastSyncAt(System.currentTimeMillis())
        return SyncResult.Success
    }

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

    private suspend fun syncVisitPhotos(
        session: com.brbrs.qarib.auth.QaribSession,
        visits: List<VisitEntity>,
        remotePhotoCount: Map<String, Int>,
    ): List<VisitEntity> {
        var folderEnsured = false
        return visits.map { visit ->
            if (visit.deleted) return@map visit
            val localPaths = parsePhotoPaths(visit.photoPaths)
            val remoteCount = remotePhotoCount[visit.id] ?: 0
            when {
                localPaths.isNotEmpty() -> {
                    if (!folderEnsured) {
                        webDavClient.ensureVisitPhotosFolder(session)
                        folderEnsured = true
                    }
                    localPaths.forEachIndexed { index, path ->
                        val bytes = photoStorage.readPhotoBytes(path)
                        if (bytes != null) {
                            webDavClient.uploadBytes(session, "visit-photos/${visit.id}-$index.jpg", bytes)
                        }
                    }
                    visit
                }
                remoteCount > 0 -> {
                    if (!folderEnsured) {
                        webDavClient.ensureVisitPhotosFolder(session)
                        folderEnsured = true
                    }
                    val downloadedPaths = mutableListOf<String>()
                    for (index in 0 until remoteCount) {
                        when (val result = webDavClient.downloadBytes(session, "visit-photos/${visit.id}-$index.jpg")) {
                            is NextcloudWebDavClient.Result.Success -> {
                                val path = photoStorage.savePhotoBytes(result.data, "visit-${visit.id}-$index")
                                if (path != null) downloadedPaths.add(path)
                            }
                            else -> {}
                        }
                    }
                    if (downloadedPaths.isNotEmpty()) visit.copy(photoPaths = serializePhotoPaths(downloadedPaths)) else visit
                }
                else -> visit
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

    private fun mergeVisitsByLastWrite(local: List<VisitEntity>, remote: List<VisitEntity>): List<VisitEntity> {
        val byId = mutableMapOf<String, VisitEntity>()
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
