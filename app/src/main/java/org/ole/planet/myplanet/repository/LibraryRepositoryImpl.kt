package org.ole.planet.myplanet.repository

import javax.inject.Inject
import io.realm.Sort
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmRemovedLog

class LibraryRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), LibraryRepository {

    override suspend fun getAllLibraryItems(): List<RealmMyLibrary> {
        return queryList(RealmMyLibrary::class.java)
    }

    override suspend fun getLibraryItemById(id: String): RealmMyLibrary? {
        return findByField(RealmMyLibrary::class.java, "id", id)
    }

    override suspend fun getOfflineLibraryItems(): List<RealmMyLibrary> {
        return queryList(RealmMyLibrary::class.java) {
            equalTo("resourceOffline", true)
        }
    }

    override suspend fun getLibraryListForUser(
        userId: String?,
        orderBy: String?,
        ascending: Boolean,
    ): List<RealmMyLibrary> {
        val results = queryList(RealmMyLibrary::class.java) {
            equalTo("isPrivate", false)
            orderBy?.let {
                sort(it, if (ascending) Sort.ASCENDING else Sort.DESCENDING)
            }
        }
        return filterLibrariesNeedingUpdate(results)
            .filter { it.userId?.contains(userId) == true }
    }

    override suspend fun getAllLibraryList(
        orderBy: String?,
        ascending: Boolean,
    ): List<RealmMyLibrary> {
        val results = queryList(RealmMyLibrary::class.java) {
            equalTo("resourceOffline", false)
            orderBy?.let {
                sort(it, if (ascending) Sort.ASCENDING else Sort.DESCENDING)
            }
        }
        return filterLibrariesNeedingUpdate(results)
    }

    override suspend fun getCourseLibraryItems(courseIds: List<String>): List<RealmMyLibrary> {
        return queryList(RealmMyLibrary::class.java) {
            `in`("courseId", courseIds.toTypedArray())
            equalTo("resourceOffline", false)
            isNotNull("resourceLocalAddress")
        }
    }

    override suspend fun saveLibraryItem(item: RealmMyLibrary) {
        save(item)
    }

    override suspend fun markResourceAdded(userId: String?, resourceId: String) {
        executeTransaction { realm ->
            RealmRemovedLog.onAdd(realm, "resources", userId, resourceId)
        }
    }

    override suspend fun deleteLibraryItem(id: String) {
        delete(RealmMyLibrary::class.java, "id", id)
    }

    override suspend fun updateLibraryItem(id: String, updater: (RealmMyLibrary) -> Unit) {
        update(RealmMyLibrary::class.java, "id", id, updater)
    }

    private fun filterLibrariesNeedingUpdate(results: Collection<RealmMyLibrary>): List<RealmMyLibrary> {
        return results.filter { it.needToUpdate() }
    }
}
