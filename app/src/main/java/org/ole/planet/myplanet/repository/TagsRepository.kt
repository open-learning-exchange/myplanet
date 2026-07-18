package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.TagEntity

interface TagsRepository {
    suspend fun getTags(dbType: String?): List<TagEntity>
    suspend fun getTagsWithChildren(dbType: String?): Map<TagEntity, List<TagEntity>>
    suspend fun getTagsForResource(resourceId: String): List<TagEntity>
    suspend fun getTagsForCourse(courseId: String): List<TagEntity>
    suspend fun getTagsForResources(resourceIds: List<String>): Map<String, List<TagEntity>>
    suspend fun getTagsForCourses(courseIds: List<String>): Map<String, List<TagEntity>>
    suspend fun insert(documentList: List<JsonObject>)
}
