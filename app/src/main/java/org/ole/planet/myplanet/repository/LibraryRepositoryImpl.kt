package org.ole.planet.myplanet.repository

import io.realm.RealmResults
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary

class LibraryRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService
) : LibraryRepository {

    override suspend fun getAllLibraryItemsAsync(): List<RealmMyLibrary> {
        return databaseService.withRealmAsync { realm ->
            realm.where(RealmMyLibrary::class.java).findAll()
        }
    }

    override suspend fun getLibraryItemByIdAsync(id: String): RealmMyLibrary? {
        return databaseService.withRealmAsync { realm ->
            realm.where(RealmMyLibrary::class.java)
                .equalTo("id", id)
                .findFirst()
        }
    }

    override suspend fun getOfflineLibraryItemsAsync(): List<RealmMyLibrary> {
        return databaseService.withRealmAsync { realm ->
            realm.where(RealmMyLibrary::class.java)
                .equalTo("resourceOffline", true)
                .findAll()
        }
    }

    override suspend fun getLibraryListForUserAsync(userId: String?): List<RealmMyLibrary> {
        return databaseService.withRealmAsync { realm ->
            val results = realm.where(RealmMyLibrary::class.java)
                .equalTo("isPrivate", false)
                .findAll()
            filterLibrariesNeedingUpdate(results).filter { it.userId?.contains(userId) == true }
        }
    }

    override suspend fun getAllLibraryListAsync(): List<RealmMyLibrary> {
        return databaseService.withRealmAsync { realm ->
            val results = realm.where(RealmMyLibrary::class.java)
                .equalTo("resourceOffline", false)
                .findAll()
            filterLibrariesNeedingUpdate(results)
        }
    }

    override suspend fun getCourseLibraryItems(courseIds: List<String>): List<RealmMyLibrary> {
        return databaseService.withRealmAsync { realm ->
            realm.where(RealmMyLibrary::class.java)
                .`in`("courseId", courseIds.toTypedArray())
                .equalTo("resourceOffline", false)
                .isNotNull("resourceLocalAddress")
                .findAll()
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

    private fun filterLibrariesNeedingUpdate(results: RealmResults<RealmMyLibrary>): List<RealmMyLibrary> {
        val libraries = mutableListOf<RealmMyLibrary>()
        for (lib in results) {
            if (lib.needToUpdate()) {
                libraries.add(lib)
            }
        }
        return libraries
    }

    override fun getAllLibraryItems(): List<RealmMyLibrary> {
        return databaseService.withRealm { realm ->
            realm.copyFromRealm(
                realm.where(RealmMyLibrary::class.java).findAll()
            )
        }
    }

    override fun getLibraryItemById(id: String): RealmMyLibrary? {
        return databaseService.withRealm { realm ->
            realm.where(RealmMyLibrary::class.java)
                .equalTo("id", id)
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        }
    }

    override fun getOfflineLibraryItems(): List<RealmMyLibrary> {
        return databaseService.withRealm { realm ->
            realm.copyFromRealm(
                realm.where(RealmMyLibrary::class.java)
                    .equalTo("resourceOffline", true)
                    .findAll()
            )
        }
    }

    override fun getLibraryListForUser(userId: String?): List<RealmMyLibrary> {
        return databaseService.withRealm { realm ->
            val results = realm.where(RealmMyLibrary::class.java)
                .equalTo("isPrivate", false)
                .findAll()
            realm.copyFromRealm(
                filterLibrariesNeedingUpdate(results).filter { it.userId?.contains(userId) == true }
            )
        }
    }

    override fun getAllLibraryList(): List<RealmMyLibrary> {
        return databaseService.withRealm { realm ->
            val results = realm.where(RealmMyLibrary::class.java)
                .equalTo("resourceOffline", false)
                .findAll()
            realm.copyFromRealm(filterLibrariesNeedingUpdate(results))
        }
    }
}
