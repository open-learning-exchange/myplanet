package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.realm.Sort
import java.io.File
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmResourceActivity
import org.ole.planet.myplanet.model.RealmSearchActivity
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.utils.DownloadUtils
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.UrlUtils

class ResourcesRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    databaseService: DatabaseService,
    private val activitiesRepository: ActivitiesRepository,
    @param:AppPreferences private val settings: SharedPreferences,
    private val sharedPrefManager: org.ole.planet.myplanet.services.SharedPrefManager,
    private val ratingsRepository: RatingsRepository,
    private val tagsRepository: TagsRepository
) : RealmRepository(databaseService), ResourcesRepository {

    override suspend fun getAllLibraries(): List<RealmMyLibrary> {
        return withRealm { realm ->
            realm.copyFromRealm(realm.where(RealmMyLibrary::class.java).findAll())
        }
    }

    override suspend fun getAllLibraryItems(): List<RealmMyLibrary> {
        return queryList(RealmMyLibrary::class.java) {
            equalTo("isPrivate", false)
        }
    }

    override suspend fun getLibraryItemById(id: String): RealmMyLibrary? {
        return findByField(RealmMyLibrary::class.java, "id", id, true)
    }

    override suspend fun getLibraryItemByResourceId(resourceId: String): RealmMyLibrary? {
        return findByField(RealmMyLibrary::class.java, "resourceId", resourceId)
            ?: findByField(RealmMyLibrary::class.java, "_id", resourceId)
    }

    override suspend fun getLibraryItemsByIds(ids: Collection<String>): List<RealmMyLibrary> {
        if (ids.isEmpty()) return emptyList()

        return queryList(RealmMyLibrary::class.java) {
            this.`in`("_id", ids.toTypedArray())
        }
    }

    override suspend fun getLibraryItemsByLocalAddress(localAddress: String): List<RealmMyLibrary> {
        return queryList(RealmMyLibrary::class.java) {
            equalTo("resourceLocalAddress", localAddress)
        }
    }

    override suspend fun getLibraryListForUser(userId: String?): List<RealmMyLibrary> {
        if (userId == null) return emptyList()

        val results = queryList(RealmMyLibrary::class.java) {
            equalTo("isPrivate", false)
        }
        return filterLibrariesNeedingUpdate(results)
            .filter { it.userId?.contains(userId) == true }
    }

    override suspend fun getLibraryForSelectedUser(userId: String): List<RealmMyLibrary> {
        return getLibraryListForUser(userId)
    }

    override suspend fun getMyLibrary(userId: String?): List<RealmMyLibrary> {
        return queryList(RealmMyLibrary::class.java) {
            equalTo("userId", userId)
        }
    }

    override suspend fun getStepResources(stepId: String?, resourceOffline: Boolean): List<RealmMyLibrary> {
        if (stepId == null) return emptyList()

        return queryList(RealmMyLibrary::class.java) {
            equalTo("stepId", stepId)
            equalTo("resourceOffline", resourceOffline)
            isNotNull("resourceLocalAddress")
        }
    }

    override suspend fun getAllStepResources(stepId: String?): List<RealmMyLibrary> {
        if (stepId == null) return emptyList()

        return queryList(RealmMyLibrary::class.java) {
            equalTo("stepId", stepId)
        }
    }

    override suspend fun countLibrariesNeedingUpdate(userId: String?): Int {
        if (userId == null) return 0

        val results = queryList(RealmMyLibrary::class.java) {
            equalTo("isPrivate", false)
        }
        return filterLibrariesNeedingUpdate(results)
            .count { it.userId?.contains(userId) == true }
    }

    override suspend fun saveLibraryItem(item: RealmMyLibrary) {
        save(item)
    }

    override suspend fun markResourceAdded(userId: String?, resourceId: String) {
        activitiesRepository.markResourceAdded(userId, resourceId)
    }

    override suspend fun updateUserLibrary(
        resourceId: String,
        userId: String,
        isAdd: Boolean,
    ): RealmMyLibrary? {
        executeTransaction { realm ->
            realm.where(RealmMyLibrary::class.java)
                .equalTo("resourceId", resourceId)
                .findFirst()?.let { library ->
                    if (isAdd) {
                        library.setUserId(userId)
                    } else {
                        library.removeUserId(userId)
                    }
                }
        }
        if (isAdd) {
            activitiesRepository.markResourceAdded(userId, resourceId)
        } else {
            activitiesRepository.markResourceRemoved(userId, resourceId)
        }
        return getLibraryItemByResourceId(resourceId)
            ?: getLibraryItemById(resourceId)
    }

    override suspend fun updateLibraryItem(id: String, updater: (RealmMyLibrary) -> Unit) {
        update(RealmMyLibrary::class.java, "id", id, updater)
    }

    override suspend fun markResourceOfflineByUrl(url: String) {
        val localAddress = FileUtils.getFileNameFromUrl(url)
        if (localAddress.isNotBlank()) {
            markResourceOfflineByLocalAddress(localAddress)
        }
    }

    override suspend fun markResourceOfflineByLocalAddress(localAddress: String) {
        executeTransaction { realm ->
            realm.where(RealmMyLibrary::class.java)
                .equalTo("resourceLocalAddress", localAddress)
                .findAll()
                ?.forEach { library ->
                    library.resourceOffline = true
                    library.downloadedRev = library._rev
                }
        }
    }

    private fun filterLibrariesNeedingUpdate(results: Collection<RealmMyLibrary>): List<RealmMyLibrary> {
        return results.filter { it.needToUpdate() }
    }

    override suspend fun getPrivateImageUrlsCreatedAfter(timestamp: Long): List<String> {
        val imageList = queryList(RealmMyLibrary::class.java) {
            equalTo("isPrivate", true)
                .greaterThan("createdDate", timestamp)
                .equalTo("mediaType", "image")
        }
        return imageList.mapNotNull { it.resourceRemoteAddress }
    }

    override suspend fun getRecentResources(userId: String): Flow<List<RealmMyLibrary>> {
        return queryListFlow(RealmMyLibrary::class.java) {
            equalTo("userId", userId)
                .sort("createdDate", Sort.DESCENDING)
                .limit(10)
        }
    }

    override suspend fun getPendingDownloads(userId: String): Flow<List<RealmMyLibrary>> {
        return queryListFlow(RealmMyLibrary::class.java) {
            equalTo("userId", userId)
                .equalTo("resourceOffline", false)
                .isNotNull("resourceLocalAddress")
        }
    }

    override suspend fun getPrivateImagesCreatedAfter(timestamp: Long): List<RealmMyLibrary> {
        return queryList(RealmMyLibrary::class.java) {
            equalTo("isPrivate", true)
                .greaterThan("createdDate", timestamp)
                .equalTo("mediaType", "image")
        }
    }

    override suspend fun markAllResourcesOffline(isOffline: Boolean) {
        executeTransaction { realm ->
            val libraries = realm.where(RealmMyLibrary::class.java).findAll()
            for (library in libraries) {
                library.resourceOffline = isOffline
            }
        }
    }

    override suspend fun saveSearchActivity(
        userName: String,
        searchText: String,
        planetCode: String,
        parentCode: String,
        tags: List<RealmTag>,
        subjects: Set<String>,
        languages: Set<String>,
        levels: Set<String>,
        mediums: Set<String>
    ) {
        val filter = JsonObject().apply {
            add("tags", RealmTag.getTagsArray(tags))
            add("subjects", getJsonArrayFromList(subjects))
            add("language", getJsonArrayFromList(languages))
            add("level", getJsonArrayFromList(levels))
            add("mediaType", getJsonArrayFromList(mediums))
        }
        val filterPayload = Gson().toJson(filter)

        executeTransaction { realm ->
            val activity = realm.createObject(RealmSearchActivity::class.java, UUID.randomUUID().toString())
            activity.user = userName
            activity.time = Calendar.getInstance().timeInMillis
            activity.createdOn = planetCode
            activity.parentCode = parentCode
            activity.text = searchText
            activity.type = "resources"
            activity.filter = filterPayload
        }
    }

    private fun getJsonArrayFromList(list: Set<String>): JsonArray {
        val array = JsonArray()
        list.forEach { array.add(it) }
        return array
    }

    override suspend fun downloadResources(resources: List<RealmMyLibrary>): Boolean {
        return try {
            val urls = resources.mapNotNull { it.resourceRemoteAddress }
            if (urls.isNotEmpty()) {
                DownloadUtils.openPriorityDownloadService(context, ArrayList(urls))
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun downloadResourcesPriority(resources: List<RealmMyLibrary>): Boolean {
        return try {
            val urls = resources.mapNotNull { it.resourceRemoteAddress }
            if (urls.isNotEmpty()) {
                DownloadUtils.openPriorityDownloadService(context, ArrayList(urls))
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getAllLibrariesToSync(): List<RealmMyLibrary> {
        return queryList(RealmMyLibrary::class.java) {
            equalTo("resourceOffline", false)
        }.filter { it.needToUpdate() }
    }

    override suspend fun addResourcesToUserLibrary(resourceIds: List<String>, userId: String) {
        if (resourceIds.isEmpty() || userId.isBlank()) return

        executeTransaction { realm ->
            val chunkSize = 1000
            resourceIds.chunked(chunkSize).forEach { chunk ->
                val libraryItems = realm.where(RealmMyLibrary::class.java)
                    .`in`("resourceId", chunk.toTypedArray())
                    .findAll()

                libraryItems.forEach { libraryItem ->
                    if (libraryItem.userId?.contains(userId) == false) {
                        libraryItem.setUserId(userId)
                    }
                }

                val removedLogs = realm.where(org.ole.planet.myplanet.model.RealmRemovedLog::class.java)
                    .equalTo("type", "resources")
                    .equalTo("userId", userId)
                    .`in`("docId", chunk.toTypedArray())
                    .findAll()

                removedLogs.deleteAllFromRealm()
            }
        }
    }

    override suspend fun addAllResourcesToUserLibrary(resources: List<RealmMyLibrary>, userId: String) {
        val resourceIds = resources.mapNotNull { it.resourceId }
        addResourcesToUserLibrary(resourceIds, userId)
    }

    override suspend fun getOpenedResourceIds(userId: String): Set<String> {
        val user = queryList(RealmUser::class.java) { equalTo("id", userId) }.firstOrNull()
        val userName = user?.name ?: return emptySet()

        return queryList(RealmResourceActivity::class.java) {
            equalTo("user", userName)
            equalTo("type", "resource_opened")
        }.mapNotNull { it.resourceId }.toSet()
    }

    override suspend fun observeOpenedResourceIds(userId: String): Flow<Set<String>> {
        val user = queryList(RealmUser::class.java) { equalTo("id", userId) }.firstOrNull()
        val userName = user?.name ?: return flowOf(emptySet())

        return queryListFlow(RealmResourceActivity::class.java) {
            equalTo("user", userName)
            equalTo("type", "resource_opened")
        }.map { activities -> activities.mapNotNull { it.resourceId }.toSet() }
    }

    override suspend fun getDownloadSuggestionList(userId: String?): List<RealmMyLibrary> {
        val targetUserId = userId ?: sharedPrefManager.getUserId().ifEmpty { null }
        val results = queryList(RealmMyLibrary::class.java) {
            equalTo("isPrivate", false)
        }
        val allNeedingUpdate = filterLibrariesNeedingUpdate(results)

        if (!targetUserId.isNullOrBlank()) {
            val userLibraries = allNeedingUpdate.filter { it.userId?.contains(targetUserId) == true }
            if (userLibraries.isNotEmpty()) {
                return userLibraries
            }
        }

        return allNeedingUpdate
    }

    override suspend fun getLibraryByUserId(userId: String): List<RealmMyLibrary> {
        val teamIds = queryList(RealmMyTeam::class.java) {
            equalTo("userId", userId)
            equalTo("docType", "membership")
        }.mapNotNull { it.teamId }

        val resourceIdsFromTeams = if (teamIds.isNotEmpty()) {
            queryList(RealmMyTeam::class.java) {
                `in`("teamId", teamIds.toTypedArray())
                equalTo("docType", "resourceLink")
            }.mapNotNull { it.resourceId }
        } else {
            emptyList()
        }

        return queryList(RealmMyLibrary::class.java) {
            beginGroup()
            equalTo("userId", userId)
            if (resourceIdsFromTeams.isNotEmpty()) {
                or()
                `in`("resourceId", resourceIdsFromTeams.toTypedArray())
            }
            endGroup()
        }
    }

    override suspend fun removeDeletedResources(currentIds: List<String?>) {
        val validCurrentIds = currentIds.filterNotNull().toSet()
        executeTransaction { realm ->
            realm.where(RealmMyLibrary::class.java)
                .isNotNull("_rev")
                .notEqualTo("_rev", "")
                .equalTo("isPrivate", false)
                .findAll()
                .filter { it.resourceId !in validCurrentIds }
                .forEach { it.deleteFromRealm() }
        }
    }

    override suspend fun getMyLibIds(userId: String): JsonArray {
        val libs = queryList(RealmMyLibrary::class.java) {
            equalTo("userId", userId)
        }
        val jsonArray = JsonArray()
        libs.forEach { jsonArray.add(it.id) }
        return jsonArray
    }

    override suspend fun removeResourceFromShelf(resourceId: String, userId: String) {
        updateUserLibrary(resourceId, userId, false)
    }

    override suspend fun getHtmlResourceDownloadUrls(resourceId: String): ResourceUrlsResponse {
        val resource = getLibraryItemByResourceId(resourceId) ?: return ResourceUrlsResponse.ResourceNotFound
        if (resource.attachments.isNullOrEmpty()) return ResourceUrlsResponse.NoAttachments

        val urls = resource.attachments?.mapNotNull { attachment ->
            attachment.name?.let { name ->
                val baseDir = File(context.getExternalFilesDir(null), "ole/$resourceId")
                val lastSlashIndex = name.lastIndexOf('/')
                if (lastSlashIndex > 0) {
                    val dirPath = name.substring(0, lastSlashIndex)
                    File(baseDir, dirPath).mkdirs()
                }
                UrlUtils.getUrl(resourceId, name)
            }
        }

        return if (!urls.isNullOrEmpty()) {
            ResourceUrlsResponse.Success(urls)
        } else {
            ResourceUrlsResponse.Error
        }
    }

    override suspend fun getFilterFacets(libraries: List<RealmMyLibrary>): Map<String, Set<String>> {
        return mapOf(
            "languages" to libraries.mapNotNull { it.language }.filterNot { it.isBlank() }.toSet(),
            "subjects" to libraries.flatMap { it.subject ?: emptyList() }.toSet(),
            "mediums" to libraries.mapNotNull { it.mediaType }.filterNot { it.isBlank() }.toSet(),
            "levels" to libraries.flatMap { it.level ?: emptyList() }.toSet()
        )
    }

    override suspend fun batchInsertResources(documents: List<JsonObject>): List<String> {
        return try {
            val savedIds = mutableListOf<String>()
            val chunkSize = 50
            documents.chunked(chunkSize).forEach { chunk ->
                executeTransaction { realmTx ->
                    val chunkDocuments = JsonArray()
                    chunk.forEach { doc ->
                        val wrapper = JsonObject()
                        wrapper.add("doc", doc)
                        chunkDocuments.add(wrapper)
                    }
                    savedIds.addAll(RealmMyLibrary.save(chunkDocuments, realmTx))
                }
            }
            savedIds
        } catch (e: Exception) {
            e.printStackTrace()
            val savedIds = mutableListOf<String>()
            documents.forEach { doc ->
                try {
                    executeTransaction { realmTx ->
                        val singleDocArray = JsonArray()
                        val wrapper = JsonObject()
                        wrapper.add("doc", doc)
                        singleDocArray.add(wrapper)
                        savedIds.addAll(RealmMyLibrary.save(singleDocArray, realmTx))
                    }
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }
            savedIds
        }
    }

    override suspend fun getResourceRatings(resourceId: String): JsonObject? {
        return ratingsRepository.getRatingsById("resource", resourceId, null)
    }

    override suspend fun getResourceTags(resourceId: String): List<RealmTag> {
        return tagsRepository.getTagsForResource(resourceId)
    }

    override suspend fun getResourceRatingsBulk(ids: List<String>, userId: String?): Map<String?, JsonObject> {
        val allRatings = ratingsRepository.getResourceRatings(userId)
        val filteredRatings = HashMap<String?, JsonObject>()
        for (id in ids) {
            allRatings[id]?.let {
                filteredRatings[id] = it
            }
        }
        return filteredRatings
    }

    override suspend fun getResourceTagsBulk(ids: List<String>): Map<String, List<RealmTag>> {
        return tagsRepository.getTagsForResources(ids)
    }
}
