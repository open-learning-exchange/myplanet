package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmTag

interface TagRepository {
    suspend fun getTags(dbType: String?): List<RealmTag>
    suspend fun buildChildMap(): HashMap<String, List<RealmTag>>
    suspend fun getTagsForResource(resourceId: String): List<RealmTag>
    suspend fun getTagsForResources(resourceIds: List<String>): Map<String, List<RealmTag>>
    suspend fun getTagsForCourse(courseId: String): List<RealmTag>
}
