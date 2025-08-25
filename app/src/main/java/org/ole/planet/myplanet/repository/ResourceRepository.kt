package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmSearchActivity
import org.ole.planet.myplanet.model.RealmTag

interface ResourceRepository {
    suspend fun getAllLibraryItems(): List<RealmMyLibrary>
    suspend fun getLibraryItemById(id: String): RealmMyLibrary?
    suspend fun getOfflineLibraryItems(): List<RealmMyLibrary>
    suspend fun getLibraryListForUser(userId: String?): List<RealmMyLibrary>
    suspend fun getAllLibraryList(): List<RealmMyLibrary>
    suspend fun getCourseLibraryItems(courseIds: List<String>): List<RealmMyLibrary>
    suspend fun getRatings(type: String, modelId: String?, userId: String?): HashMap<String?, JsonObject>?
    suspend fun getLibraryList(): List<RealmMyLibrary?>
    suspend fun saveSearchActivity(activity: RealmSearchActivity)
    suspend fun getResourceTags(resourceId: String?): List<RealmTag>
    suspend fun getParentTag(tagId: String?): RealmTag?
    suspend fun saveLibraryItem(item: RealmMyLibrary)
    suspend fun deleteLibraryItem(id: String)
    suspend fun updateLibraryItem(id: String, updater: (RealmMyLibrary) -> Unit)
}
