package org.ole.planet.myplanet.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.realm.Sort
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmResourceActivity
import org.ole.planet.myplanet.model.RealmSearchActivity
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.utils.DownloadUtils
import org.ole.planet.myplanet.utils.FileUtils

class ResourcesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    databaseService: DatabaseService,
    private val activitiesRepository: ActivitiesRepository
) : RealmRepository(databaseService), ResourcesRepository {

    override suspend fun getAllLibraryItems(): List<RealmMyLibrary> {
        return queryList(RealmMyLibrary::class.java)
    }

    override suspend fun getLibraryItemById(id: String): RealmMyLibrary? {
        return findByField(RealmMyLibrary::class.java, "id", id)
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
                DownloadUtils.openDownloadService(context, ArrayList(urls), false)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
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
            resourceIds.forEach { resourceId ->
                val libraryItem = realm.where(RealmMyLibrary::class.java)
                    .equalTo("resourceId", resourceId)
                    .findFirst()

                libraryItem?.let {
                    if (it.userId?.contains(userId) == false) {
                        it.setUserId(userId)
                    }
                }

                val removedLog = realm.where(org.ole.planet.myplanet.model.RealmRemovedLog::class.java)
                    .equalTo("type", "resources")
                    .equalTo("userId", userId)
                    .equalTo("docId", resourceId)
                    .findFirst()

                removedLog?.deleteFromRealm()
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
        val results = queryList(RealmMyLibrary::class.java) {
            equalTo("isPrivate", false)
        }
        val allNeedingUpdate = filterLibrariesNeedingUpdate(results)

        if (!userId.isNullOrBlank()) {
            val userLibraries = allNeedingUpdate.filter { it.userId?.contains(userId) == true }
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
            val allResources = realm.where(RealmMyLibrary::class.java).findAll()
            val idsToDelete = allResources.mapNotNull { it.resourceId }.filter { it !in validCurrentIds }

            if (idsToDelete.isNotEmpty()) {
                val chunkSize = 1000
                idsToDelete.chunked(chunkSize).forEach { chunk ->
                    realm.where(RealmMyLibrary::class.java)
                        .`in`("resourceId", chunk.toTypedArray())
                        .findAll()
                        .deleteAllFromRealm()
                }
            }
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
}
