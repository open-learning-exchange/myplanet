package org.ole.planet.myplanet.repository

import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmMyLibrary

interface LibraryRepository {
    suspend fun getAllLibraryItems(): List<RealmMyLibrary>
    suspend fun getLibraryItemById(id: String): RealmMyLibrary?
    suspend fun getLibraryItemByResourceId(resourceId: String): RealmMyLibrary?
    suspend fun getLibraryItemsByIds(ids: Collection<String>): List<RealmMyLibrary>
    suspend fun getLibraryItemsByLocalAddress(localAddress: String): List<RealmMyLibrary>
    suspend fun getLibraryListForUser(userId: String?): List<RealmMyLibrary>
    suspend fun getLibraryForSelectedUser(userId: String): List<RealmMyLibrary>
    suspend fun getMyLibrary(userId: String?): List<RealmMyLibrary>
    suspend fun getStepResources(stepId: String?, resourceOffline: Boolean): List<RealmMyLibrary>
    suspend fun getRecentResources(userId: String): Flow<List<RealmMyLibrary>>
    suspend fun getPendingDownloads(userId: String): Flow<List<RealmMyLibrary>>
    suspend fun countLibrariesNeedingUpdate(userId: String?): Int
    suspend fun saveLibraryItem(item: RealmMyLibrary)
    suspend fun markResourceAdded(userId: String?, resourceId: String)
    suspend fun updateUserLibrary(resourceId: String, userId: String, isAdd: Boolean): RealmMyLibrary?
    suspend fun updateLibraryItem(id: String, updater: (RealmMyLibrary) -> Unit)
    suspend fun markResourceOfflineByLocalAddress(localAddress: String)
}
