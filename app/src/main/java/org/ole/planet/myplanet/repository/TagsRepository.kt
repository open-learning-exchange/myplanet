package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.RealmTag

interface TagsRepository {
    suspend fun getTags(dbType: String?): List<RealmTag>
    suspend fun insertFromJson(act: JsonObject)
    suspend fun insertTagsList(docs: List<JsonObject>)
    fun getTagsArray(tags: List<RealmTag>): JsonArray
    suspend fun buildChildMap(): HashMap<String, List<RealmTag>>
    suspend fun getTagsForResource(resourceId: String): List<RealmTag>
    suspend fun getTagsForCourse(courseId: String): List<RealmTag>
    suspend fun getTagsForResources(resourceIds: List<String>): Map<String, List<RealmTag>>
}
