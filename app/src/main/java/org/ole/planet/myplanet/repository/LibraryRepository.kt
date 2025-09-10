package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyLibrary

interface LibraryRepository {
    suspend fun getAllLibraryItems(): List<RealmMyLibrary>
    suspend fun getLibraryItemById(id: String): RealmMyLibrary?
    suspend fun getLibraryItemByResourceId(resourceId: String): RealmMyLibrary?
    suspend fun getLibraryItemsByLocalAddress(localAddress: String): List<RealmMyLibrary>
    suspend fun getOfflineLibraryItems(): List<RealmMyLibrary>
    suspend fun getLibraryListForUser(userId: String?): List<RealmMyLibrary>
    suspend fun getAllLibraryList(): List<RealmMyLibrary>
    suspend fun getCourseLibraryItems(courseIds: List<String>): List<RealmMyLibrary>
    suspend fun saveLibraryItem(item: RealmMyLibrary)
    suspend fun markResourceAdded(userId: String?, resourceId: String)
    suspend fun deleteLibraryItem(id: String)
    suspend fun updateLibraryItem(id: String, updater: (RealmMyLibrary) -> Unit)
}
