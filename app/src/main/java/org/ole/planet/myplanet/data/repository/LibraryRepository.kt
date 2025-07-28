package org.ole.planet.myplanet.data.repository

import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary

interface LibraryRepository {
    fun getAllLibraryItems(): List<RealmMyLibrary>
    fun getLibraryItemById(id: String): RealmMyLibrary?
    fun getOfflineLibraryItems(): List<RealmMyLibrary>
}

class LibraryRepositoryImpl(
    private val databaseService: DatabaseService,
    private val apiInterface: ApiInterface
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
}
