package org.ole.planet.myplanet.repository

import javax.inject.Inject
import com.google.gson.JsonObject
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmSearchActivity
import org.ole.planet.myplanet.model.RealmTag

class ResourceRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), ResourceRepository {

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

    override suspend fun getLibraryListForUser(userId: String?): List<RealmMyLibrary> {
        val results = queryList(RealmMyLibrary::class.java) {
            equalTo("isPrivate", false)
        }
        return filterLibrariesNeedingUpdate(results)
            .filter { it.userId?.contains(userId) == true }
    }

    override suspend fun getAllLibraryList(): List<RealmMyLibrary> {
        val results = queryList(RealmMyLibrary::class.java) {
            equalTo("resourceOffline", false)
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

    override suspend fun deleteLibraryItem(id: String) {
        delete(RealmMyLibrary::class.java, "id", id)
    }

    override suspend fun updateLibraryItem(id: String, updater: (RealmMyLibrary) -> Unit) {
        update(RealmMyLibrary::class.java, "id", id, updater)
    }

    private fun filterLibrariesNeedingUpdate(results: Collection<RealmMyLibrary>): List<RealmMyLibrary> {
        return results.filter { it.needToUpdate() }
    }

    override suspend fun getRatings(type: String, modelId: String?, userId: String?): HashMap<String?, JsonObject>? {
        return RealmRating.getRatings(realm, type, modelId, userId)
    }

    override suspend fun getLibraryList(): List<RealmMyLibrary?> {
        return getList(RealmMyLibrary::class.java)
    }

    override suspend fun saveSearchActivity(activity: RealmSearchActivity) {
        save(activity)
    }

    override suspend fun getResourceTags(resourceId: String?): List<RealmTag> {
        return queryList(RealmTag::class.java) {
            equalTo("db", "resources")
            equalTo("linkId", resourceId)
        }
    }

    override suspend fun getParentTag(tagId: String?): RealmTag? {
        return findByField(RealmTag::class.java, "id", tagId)
    }
}
