package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmTag

class TagRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), TagRepository {

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

    override suspend fun getAllCourseTags(): Map<String, List<RealmTag>> {
        return withRealmAsync { realm ->
            // 1. Get all link tags for courses
            val links = realm.where(RealmTag::class.java)
                .equalTo("db", "courses")
                .isNotNull("linkId")
                .isNotNull("tagId")
                .findAll()

            // 2. Group tagIds by linkId (courseId)
            val tagIdsByCourseId = HashMap<String, MutableSet<String>>()
            links.forEach { link ->
                val courseId = link.linkId!!
                val tagId = link.tagId!!
                if (!tagIdsByCourseId.containsKey(courseId)) {
                    tagIdsByCourseId[courseId] = HashSet()
                }
                tagIdsByCourseId[courseId]?.add(tagId)
            }

            // 3. Collect all unique tag IDs
            val allTagIds = tagIdsByCourseId.values.flatten().toSet()

            if (allTagIds.isEmpty()) {
                return@withRealmAsync emptyMap()
            }

            // 4. Fetch actual Tag objects for these IDs
            val tags = realm.where(RealmTag::class.java)
                .`in`("id", allTagIds.toTypedArray())
                .findAll()
            val unmanagedTags = realm.copyFromRealm(tags)
            val tagsById = unmanagedTags.associateBy { it.id }

            // 5. Build result map
            val result = HashMap<String, List<RealmTag>>()
            tagIdsByCourseId.forEach { (courseId, tagIds) ->
                val courseTags = tagIds.mapNotNull { tagsById[it] }
                if (courseTags.isNotEmpty()) {
                    result[courseId] = courseTags
                }
            }
            result
        }
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
