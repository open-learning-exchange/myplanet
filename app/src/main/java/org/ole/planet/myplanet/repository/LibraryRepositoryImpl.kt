package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.findCopyByField
import org.ole.planet.myplanet.datamanager.queryList
import org.ole.planet.myplanet.model.RealmMyLibrary

class LibraryRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService
) : LibraryRepository {

    override suspend fun getAllLibraryItems(): List<RealmMyLibrary> {
        return databaseService.withRealmAsync { realm ->
            realm.queryList(RealmMyLibrary::class.java)
        }
    }

    override suspend fun getLibraryItemById(id: String): RealmMyLibrary? {
        return databaseService.withRealmAsync { realm ->
            realm.findCopyByField(RealmMyLibrary::class.java, "id", id)
        }
    }

    override suspend fun getOfflineLibraryItems(): List<RealmMyLibrary> {
        return databaseService.withRealmAsync { realm ->
            realm.queryList(RealmMyLibrary::class.java) {
                equalTo("resourceOffline", true)
            }
        }
    }

    override suspend fun getLibraryListForUser(userId: String?): List<RealmMyLibrary> {
        if (userId == null) return emptyList()
        return databaseService.withRealmAsync { realm ->
            realm.queryList(RealmMyLibrary::class.java) {
                equalTo("isPrivate", false)
                contains("userId", userId)
            }
        }
    }

    override suspend fun getAllLibraryList(): List<RealmMyLibrary> {
        return databaseService.withRealmAsync { realm ->
            realm.queryList(RealmMyLibrary::class.java) {
                equalTo("resourceOffline", false)
            }
        }
    }

    override suspend fun getCourseLibraryItems(courseIds: List<String>): List<RealmMyLibrary> {
        return databaseService.withRealmAsync { realm ->
            realm.queryList(RealmMyLibrary::class.java) {
                `in`("courseId", courseIds.toTypedArray())
                equalTo("resourceOffline", false)
                isNotNull("resourceLocalAddress")
            }
        }
    }

    override suspend fun saveLibraryItem(item: RealmMyLibrary) {
        databaseService.executeTransactionAsync { realm ->
            realm.copyToRealmOrUpdate(item)
        }
    }

    override suspend fun deleteLibraryItem(id: String) {
        databaseService.executeTransactionAsync { realm ->
            realm.where(RealmMyLibrary::class.java)
                .equalTo("id", id)
                .findFirst()
                ?.deleteFromRealm()
        }
    }

    override suspend fun updateLibraryItem(id: String, updater: (RealmMyLibrary) -> Unit) {
        databaseService.executeTransactionAsync { realm ->
            realm.where(RealmMyLibrary::class.java)
                .equalTo("id", id)
                .findFirst()
                ?.let { updater(it) }
        }
    }
}
