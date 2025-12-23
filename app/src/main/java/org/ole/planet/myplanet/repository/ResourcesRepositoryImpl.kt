package org.ole.planet.myplanet.repository

import io.realm.Sort
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmRemovedLog
import org.ole.planet.myplanet.model.RealmRemovedLog.Companion.onAdd
import org.ole.planet.myplanet.model.RealmRemovedLog.Companion.onRemove

class ResourcesRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
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
        withRealmAsync { realm ->
            RealmRemovedLog.onAdd(realm, "resources", userId, resourceId)
        }
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
        withRealmAsync { realm ->
            if (isAdd) {
                onAdd(realm, "resources", userId, resourceId)
            } else {
                onRemove(realm, "resources", userId, resourceId)
            }
        }
        return getLibraryItemByResourceId(resourceId)
            ?: getLibraryItemById(resourceId)
    }

    override suspend fun updateLibraryItem(id: String, updater: (RealmMyLibrary) -> Unit) {
        update(RealmMyLibrary::class.java, "id", id, updater)
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
}
