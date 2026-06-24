package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmTag

data class LibraryWithMetadata(
    val library: RealmMyLibrary,
    val rating: JsonObject?,
    val tags: List<RealmTag>
)

data class ResourceUploadData(
    val libraryId: String?,
    val title: String?,
    val isPrivate: Boolean,
    val privateFor: String?,
    val serialized: JsonObject
)

data class UploadedResourceInfo(
    val libraryId: String,
    val id: String,
    val rev: String,
    val isPrivate: Boolean,
    val privateFor: String?,
    val title: String?
)

data class LocalResourceRequest(
    val title: String?,
    val addedBy: String?,
    val author: String?,
    val year: String?,
    val description: String?,
    val publisher: String?,
    val linkToLicense: String?,
    val openWith: String?,
    val language: String?,
    val mediaType: String?,
    val resourceType: String?,
    val subjects: io.realm.RealmList<String>?,
    val levels: io.realm.RealmList<String>?,
    val resourceFor: io.realm.RealmList<String>?,
    val resourceUrl: String?,
    val userId: String?,
    val isPrivateTeamResource: Boolean,
    val teamId: String?
)

interface ResourcesRepository {
    suspend fun getAllLibraries(): List<RealmMyLibrary>
    suspend fun getAllLibraryItems(): List<RealmMyLibrary>
    suspend fun getLibraryItemById(id: String): RealmMyLibrary?
    suspend fun search(query: String, isMyCourseLib: Boolean, userId: String?): List<RealmMyLibrary>
    suspend fun getLibraryItemByResourceId(resourceId: String): RealmMyLibrary?
    suspend fun getLibraryItemsByIds(ids: Collection<String>): List<RealmMyLibrary>
    suspend fun getLibraryItemsByLocalAddress(localAddress: String): List<RealmMyLibrary>
    suspend fun getLibraryListForUser(userId: String?): List<RealmMyLibrary>
    suspend fun getLibraryForSelectedUser(userId: String): List<RealmMyLibrary>
    suspend fun getMyLibrary(userId: String?): List<RealmMyLibrary>
    suspend fun getStepResources(stepId: String?, resourceOffline: Boolean): List<RealmMyLibrary>
    suspend fun getAllStepResources(stepId: String?): List<RealmMyLibrary>
    fun getRecentResources(userId: String): Flow<List<RealmMyLibrary>>
    fun getPendingDownloads(userId: String): Flow<List<RealmMyLibrary>>
    suspend fun getPrivateImagesCreatedAfter(timestamp: Long): List<RealmMyLibrary>
    suspend fun countLibrariesNeedingUpdate(userId: String?): Int
    suspend fun resourceTitleExists(title: String): Boolean
    suspend fun saveLibraryItem(item: RealmMyLibrary)
    suspend fun saveLocalResource(request: LocalResourceRequest): Result<Unit>
    suspend fun markResourceAdded(userId: String?, resourceId: String)
    suspend fun updateUserLibrary(resourceId: String, userId: String, isAdd: Boolean): RealmMyLibrary?
    suspend fun updateLibraryItem(id: String, updater: (RealmMyLibrary) -> Unit)
    suspend fun markResourceOfflineByUrl(url: String)
    suspend fun markResourceOfflineByLocalAddress(localAddress: String)
    suspend fun markAllResourcesOffline(isOffline: Boolean)
    suspend fun saveSearchActivity(
        userName: String,
        searchText: String,
        planetCode: String,
        parentCode: String,
        tags: List<RealmTag>,
        subjects: Set<String>,
        languages: Set<String>,
        levels: Set<String>,
        mediums: Set<String>
    )
    suspend fun downloadResources(resources: List<RealmMyLibrary>): Boolean
    suspend fun downloadResourcesPriority(resources: List<RealmMyLibrary>): Boolean
    suspend fun getAllLibrariesToSync(): List<RealmMyLibrary>
    suspend fun addResourcesToUserLibrary(resourceIds: List<String>, userId: String): Result<Unit>
    suspend fun addAllResourcesToUserLibrary(resources: List<RealmMyLibrary>, userId: String): Result<Unit>
    suspend fun getOpenedResourceIds(userId: String): Set<String>
    suspend fun observeOpenedResourceIds(userId: String): Flow<Set<String>>
    suspend fun getDownloadSuggestionList(userId: String? = null): List<RealmMyLibrary>
    suspend fun getLibraryByUserId(userId: String): List<RealmMyLibrary>
    suspend fun removeDeletedResources(currentIds: List<String?>)
    suspend fun getMyLibIds(userId: String): JsonArray
    suspend fun removeResourceFromShelf(resourceId: String, userId: String)
    suspend fun getHtmlResourceDownloadUrls(resourceId: String): ResourceUrlsResponse
    suspend fun getFilterFacets(libraries: List<RealmMyLibrary>): Map<String, Set<String>>
    suspend fun batchInsertResources(documents: List<JsonObject>): List<String>
    suspend fun batchInsertMyLibrary(shelfId: String?, documents: List<JsonObject>): Int
    suspend fun getResourceRatings(resourceId: String): JsonObject?
    suspend fun getResourceTags(resourceId: String): List<RealmTag>
    suspend fun getResourceRatingsBulk(ids: List<String>, userId: String?): Map<String?, JsonObject>
    suspend fun getResourceTagsBulk(ids: List<String>): Map<String, List<RealmTag>>
    suspend fun getEnrichedLibraries(isMyCourseLib: Boolean, modelId: String?): List<LibraryWithMetadata>
    suspend fun getLibraryItemsByResourceIds(ids: Collection<String>): List<RealmMyLibrary>
    suspend fun getTeamPrivateResources(teamId: String): List<RealmMyLibrary>
    suspend fun getPublicLibraryItems(): List<RealmMyLibrary>
    suspend fun getResourceTitlesMap(): Map<String, String>
    suspend fun markResourcesAsNotOffline(resourceIds: Collection<String>)
}

sealed class ResourceUrlsResponse {
    data class Success(val urls: List<String>) : ResourceUrlsResponse()
    object ResourceNotFound : ResourceUrlsResponse()
    object NoAttachments : ResourceUrlsResponse()
    object Error : ResourceUrlsResponse()
}
