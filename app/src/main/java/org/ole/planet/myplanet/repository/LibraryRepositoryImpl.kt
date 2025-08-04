package org.ole.planet.myplanet.repository

import io.realm.RealmResults
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary

class LibraryRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService
) : LibraryRepository {

    override fun getAllLibraryItems(): List<RealmMyLibrary> {
        return databaseService.realmInstance.where(RealmMyLibrary::class.java).findAll()
    }

    override fun getLibraryItemById(id: String): RealmMyLibrary? {
        return databaseService.realmInstance.where(RealmMyLibrary::class.java)
            .equalTo("id", id)
            .findFirst()
    }

    override fun getOfflineLibraryItems(): List<RealmMyLibrary> {
        return databaseService.realmInstance.where(RealmMyLibrary::class.java)
            .equalTo("resourceOffline", true)
            .findAll()
    }

    override fun getLibraryListForUser(userId: String?): List<RealmMyLibrary> {
        val results = databaseService.realmInstance.where(RealmMyLibrary::class.java)
            .equalTo("isPrivate", false)
            .findAll()
        return filterLibrariesNeedingUpdate(results).filter { it.userId?.contains(userId) == true }
    }

    override fun getAllLibraryList(): List<RealmMyLibrary> {
        val results = databaseService.realmInstance.where(RealmMyLibrary::class.java)
            .equalTo("resourceOffline", false)
            .findAll()
        return filterLibrariesNeedingUpdate(results)
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
}
