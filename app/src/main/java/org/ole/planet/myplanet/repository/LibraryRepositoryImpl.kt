package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmRemovedLog
import org.ole.planet.myplanet.model.RealmRemovedLog.Companion.onAdd
import org.ole.planet.myplanet.model.RealmRemovedLog.Companion.onRemove

class LibraryRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), LibraryRepository {

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

        // OPTIMIZATION: Filter by userId in the query first, then check needToUpdate
        // This is faster than loading all libraries and filtering in memory
        val results = queryList(RealmMyLibrary::class.java) {
            equalTo("isPrivate", false)
            contains("userId", userId)
        }
        return filterLibrariesNeedingUpdate(results)
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

        // OPTIMIZATION: Use Realm query to filter directly in database instead of loading all into memory
        // This is much faster than loading all libraries and filtering in Kotlin
        return withRealm { realm ->
            // Count resources that need update and belong to this user
            // needToUpdate() logic: !resourceOffline OR (resourceLocalAddress != null AND _rev != downloadedRev)

            // Case 1: Resources that are not offline
            val notOfflineCount = realm.where(RealmMyLibrary::class.java)
                .equalTo("isPrivate", false)
                .equalTo("resourceOffline", false)
                .contains("userId", userId)
                .count()

            // Case 2: Resources that have local address but need update (_rev != downloadedRev)
            val needsUpdateCount = realm.where(RealmMyLibrary::class.java)
                .equalTo("isPrivate", false)
                .equalTo("resourceOffline", true)
                .isNotNull("resourceLocalAddress")
                .notEqualTo("_rev", "")  // Has a revision
                .contains("userId", userId)
                .findAll()
                .count { it._rev != it.downloadedRev }  // Only count if revisions don't match

            (notOfflineCount + needsUpdateCount).toInt()
        }
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
}
