package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmTag

class TagsRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), TagsRepository {

    override suspend fun getTags(dbType: String?): List<RealmTag> {
        return queryList(RealmTag::class.java) {
            dbType?.let { equalTo("db", it) }
            isNotEmpty("name")
            equalTo("isAttached", false)
        }
    }

    override suspend fun buildChildMap(): HashMap<String, List<RealmTag>> {
        val allTags = queryList(RealmTag::class.java)
        val childMap = HashMap<String, List<RealmTag>>()
        allTags.forEach { t ->
            t.attachedTo?.forEach { parent ->
                val list = childMap[parent]?.toMutableList() ?: mutableListOf()
                if (!list.contains(t)) {
                    list.add(t)
                }
                childMap[parent] = list
            }
        }
        return childMap
    }

    override suspend fun getTagsForResource(resourceId: String): List<RealmTag> {
        return getLinkedTags("resources", resourceId)
    }

    override suspend fun getTagsForCourse(courseId: String): List<RealmTag> {
        return getLinkedTags("courses", courseId)
    }

    private suspend fun getLinkedTags(db: String, linkId: String): List<RealmTag> {
        val links = queryList(RealmTag::class.java) {
            equalTo("db", db)
            equalTo("linkId", linkId)
        }
        if (links.isEmpty()) {
            return emptyList()
        }
        val tagIds = links.mapNotNull { it.tagId }.distinct()
        if (tagIds.isEmpty()) {
            return emptyList()
        }

        val parents = queryList(RealmTag::class.java) {
            `in`("id", tagIds.toTypedArray())
        }
        if (parents.isEmpty()) {
            return emptyList()
        }

        val parentsById = parents.associateBy { it.id }
        return tagIds.mapNotNull { parentsById[it] }
    }
}
