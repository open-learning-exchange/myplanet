package org.ole.planet.myplanet.repository

import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmMyLibrary

interface ResourcesRepository {
    suspend fun getAllLibraryItems(): List<RealmMyLibrary>
    suspend fun getLibraryItemById(id: String): RealmMyLibrary?
    suspend fun getLibraryItemByResourceId(resourceId: String): RealmMyLibrary?
    suspend fun getLibraryItemsByIds(ids: Collection<String>): List<RealmMyLibrary>
    suspend fun getLibraryItemsByLocalAddress(localAddress: String): List<RealmMyLibrary>
    suspend fun getLibraryListForUser(userId: String?): List<RealmMyLibrary>
    suspend fun getLibraryForSelectedUser(userId: String): List<RealmMyLibrary>
    suspend fun getMyLibrary(userId: String?): List<RealmMyLibrary>
    suspend fun getStepResources(stepId: String?, resourceOffline: Boolean): List<RealmMyLibrary>
    suspend fun getRecentResources(userId: String): Flow<List<RealmMyLibrary>>
    suspend fun getPendingDownloads(userId: String): Flow<List<RealmMyLibrary>>
    suspend fun getPrivateImagesCreatedAfter(timestamp: Long): List<RealmMyLibrary>
    suspend fun countLibrariesNeedingUpdate(userId: String?): Int
    suspend fun saveLibraryItem(item: RealmMyLibrary)
    suspend fun markResourceAdded(userId: String?, resourceId: String)
    suspend fun updateUserLibrary(resourceId: String, userId: String, isAdd: Boolean): RealmMyLibrary?
    suspend fun updateLibraryItem(id: String, updater: (RealmMyLibrary) -> Unit)
    suspend fun markResourceOfflineByUrl(url: String)
    suspend fun markResourceOfflineByLocalAddress(localAddress: String)
    suspend fun getPrivateImageUrlsCreatedAfter(timestamp: Long): List<String>
    suspend fun markAllResourcesOffline(isOffline: Boolean)
    suspend fun saveSearchActivity(
        userName: String,
        searchText: String,
        planetCode: String,
        parentCode: String,
        filterPayload: String
    )
    suspend fun downloadResources(resources: List<RealmMyLibrary>): Boolean
    suspend fun getAttachmentUrls(resourceId: String): AttachmentResult
}

sealed class AttachmentResult {
    data class Success(val urls: List<String>) : AttachmentResult()
    object ResourceNotFound : AttachmentResult()
    object NoAttachments : AttachmentResult()
}
