package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.RealmTag

interface TagsRepository {
    suspend fun getTags(dbType: String?): List<RealmTag>
    suspend fun getTagsWithChildren(dbType: String?): Map<RealmTag, List<RealmTag>>
    suspend fun getTagsForResource(resourceId: String): List<RealmTag>
    suspend fun getTagsForCourse(courseId: String): List<RealmTag>
    suspend fun getTagsForResources(resourceIds: List<String>): Map<String, List<RealmTag>>
    suspend fun getTagsForCourses(courseIds: List<String>): Map<String, List<RealmTag>>
    fun bulkInsertFromSync(realm: io.realm.Realm, jsonArray: JsonArray)
    suspend fun insert(documentList: List<JsonObject>)
}
