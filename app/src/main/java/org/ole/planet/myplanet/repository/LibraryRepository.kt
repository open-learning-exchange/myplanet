package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyLibrary

interface LibraryRepository {
    suspend fun getAllLibraryItemsAsync(): List<RealmMyLibrary>
    suspend fun getLibraryItemByIdAsync(id: String): RealmMyLibrary?
    suspend fun getOfflineLibraryItemsAsync(): List<RealmMyLibrary>
    suspend fun getLibraryListForUserAsync(userId: String?): List<RealmMyLibrary>
    suspend fun getAllLibraryListAsync(): List<RealmMyLibrary>
    suspend fun saveLibraryItem(item: RealmMyLibrary)
    suspend fun deleteLibraryItem(id: String)
    suspend fun updateLibraryItem(id: String, updater: (RealmMyLibrary) -> Unit)
    fun getAllLibraryItems(): List<RealmMyLibrary>
    fun getLibraryItemById(id: String): RealmMyLibrary?
    fun getOfflineLibraryItems(): List<RealmMyLibrary>
    fun getLibraryListForUser(userId: String?): List<RealmMyLibrary>
    fun getAllLibraryList(): List<RealmMyLibrary>
}
