package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.TagEntity

data class LibraryWithMetadata(
    val library: MyLibrary,
    val rating: JsonObject?,
    val tags: List<TagEntity>
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
    val subjects: List<String>?,
    val levels: List<String>?,
    val resourceFor: List<String>?,
    val resourceUrl: String?,
    val userId: String?,
    val isPrivateTeamResource: Boolean,
    val teamId: String?
)

interface ResourcesRepository {
    suspend fun getAllLibraries(): List<MyLibrary>
    suspend fun getLibraryItemById(id: String): MyLibrary?
    suspend fun search(query: String, isMyCourseLib: Boolean, userId: String?): List<MyLibrary>
    suspend fun getLibraryItemByResourceId(resourceId: String): MyLibrary?
    suspend fun getLibraryItemsByIds(ids: Collection<String>): List<MyLibrary>
    suspend fun getLibraryItemsByLocalAddress(localAddress: String): List<MyLibrary>
    suspend fun getLibraryListForUser(userId: String?): List<MyLibrary>
    suspend fun getLibraryForSelectedUser(userId: String): List<MyLibrary>
    suspend fun getMyLibrary(userId: String?): List<MyLibrary>
    suspend fun getAllStepResources(stepId: String?): List<MyLibrary>
    fun getRecentResources(userId: String): Flow<List<MyLibrary>>
    fun getPendingDownloads(userId: String): Flow<List<MyLibrary>>
    suspend fun countLibrariesNeedingUpdate(userId: String?): Int
    suspend fun resourceTitleExists(title: String): Boolean
    suspend fun saveLocalResource(request: LocalResourceRequest): Result<Unit>
    suspend fun markResourceAdded(userId: String?, resourceId: String)
    suspend fun updateUserLibrary(resourceId: String, userId: String, isAdd: Boolean): MyLibrary?
    suspend fun updateLibraryItem(id: String, updater: (MyLibrary) -> Unit)
    suspend fun markResourceOfflineByUrl(url: String)
    suspend fun markAllResourcesOffline(isOffline: Boolean)
    suspend fun saveSearchActivity(
        userName: String,
        searchText: String,
        planetCode: String,
        parentCode: String,
        tags: List<TagEntity>,
        subjects: Set<String>,
        languages: Set<String>,
        levels: Set<String>,
        mediums: Set<String>
    )
    suspend fun getResourceById(id: String): MyLibrary?
    suspend fun updateLocalResource(
        resourceId: String,
        title: String,
        author: String,
        year: String,
        description: String,
        publisher: String,
        linkToLicense: String,
        subjects: List<String>?,
        levels: List<String>?
    ): Result<Unit>
    suspend fun downloadResources(resources: List<MyLibrary>): Boolean
    suspend fun downloadResourcesPriority(resources: List<MyLibrary>): Boolean
    suspend fun getAllLibrariesToSync(): List<MyLibrary>
    suspend fun addResourcesToUserLibrary(resourceIds: List<String>, userId: String): Result<Unit>
    suspend fun addAllResourcesToUserLibrary(resources: List<MyLibrary>, userId: String): Result<Unit>
    suspend fun observeOpenedResourceIds(userId: String): Flow<Set<String>>
    suspend fun getDownloadSuggestionList(userId: String? = null): List<MyLibrary>
    suspend fun removeDeletedResources(currentIds: List<String?>)
    suspend fun getMyLibIds(userId: String): JsonArray
    suspend fun removeResourceFromShelf(resourceId: String, userId: String)
    suspend fun getHtmlResourceDownloadUrls(resourceId: String): ResourceUrlsResponse
    suspend fun getFilterFacets(libraries: List<MyLibrary>): Map<String, Set<String>>
    suspend fun batchInsertResources(documents: List<JsonObject>): List<String>
    suspend fun batchInsertMyLibrary(shelfId: String?, documents: List<JsonObject>): Int
    suspend fun getEnrichedLibraries(isMyCourseLib: Boolean, modelId: String?): List<LibraryWithMetadata>
    suspend fun getLibraryItemsByResourceIds(ids: Collection<String>): List<MyLibrary>
    suspend fun getTeamPrivateResources(teamId: String): List<MyLibrary>
    suspend fun getPublicLibraryItems(): List<MyLibrary>
    suspend fun getResourceTitlesMap(): Map<String, String>
    suspend fun markResourcesAsNotOffline(resourceIds: Collection<String>)
    suspend fun getCourseResourcesGroupedByStepId(courseId: String): Map<String?, List<MyLibrary>>
    suspend fun getPendingResourceUploads(): List<MyLibrary>
    suspend fun markResourceUploaded(localId: String, remoteId: String, remoteRev: String, planetCode: String?): Boolean
}

sealed class ResourceUrlsResponse {
    data class Success(val urls: List<String>) : ResourceUrlsResponse()
    object ResourceNotFound : ResourceUrlsResponse()
    object NoAttachments : ResourceUrlsResponse()
    object Error : ResourceUrlsResponse()
}
