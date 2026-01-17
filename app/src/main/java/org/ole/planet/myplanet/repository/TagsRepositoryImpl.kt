package org.ole.planet.myplanet.repository

import javax.inject.Inject
import javax.inject.Singleton
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmTag

@Singleton
class TagsRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), TagsRepository {

    // Cache for buildChildMap to avoid expensive O(n²) operation on every call
    @Volatile
    private var cachedChildMap: HashMap<String, List<RealmTag>>? = null
    @Volatile
    private var lastChildMapBuildTime: Long = 0
    private val cacheValidityMs = 60_000L // 1 minute cache validity
    private val cacheLock = Any()

    override suspend fun getTags(dbType: String?): List<RealmTag> {
        return queryList(RealmTag::class.java) {
            dbType?.let { equalTo("db", it) }
            isNotEmpty("name")
            equalTo("isAttached", false)
        }
    }

    override suspend fun buildChildMap(): HashMap<String, List<RealmTag>> {
        val currentTime = System.currentTimeMillis()
        
        // Check cache with synchronized block for thread safety
        synchronized(cacheLock) {
            cachedChildMap?.let { cached ->
                if (currentTime - lastChildMapBuildTime < cacheValidityMs) {
                    return HashMap(cached)
                }
            }
        }
        
        // Build new map outside synchronized block (expensive operation)
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
        
        // Update cache with synchronized block
        synchronized(cacheLock) {
            cachedChildMap = childMap
            lastChildMapBuildTime = currentTime
        }
        
        return childMap
    }

    fun invalidateChildMapCache() {
        synchronized(cacheLock) {
            cachedChildMap = null
            lastChildMapBuildTime = 0
        }
    }

    override suspend fun getTagsForResource(resourceId: String): List<RealmTag> {
        return getLinkedTags("resources", resourceId)
    }

    override suspend fun getTagsForCourse(courseId: String): List<RealmTag> {
        return getLinkedTags("courses", courseId)
    }

    override suspend fun getTagsForResources(resourceIds: List<String>): Map<String, List<RealmTag>> {
        return getLinkedTagsBulk("resources", resourceIds)
    }

    private suspend fun getLinkedTagsBulk(db: String, linkIds: List<String>): Map<String, List<RealmTag>> {
        if (linkIds.isEmpty()) {
            return emptyMap()
        }

        val links = queryList(RealmTag::class.java) {
            equalTo("db", db)
            `in`("linkId", linkIds.toTypedArray())
        }
        if (links.isEmpty()) {
            return emptyMap()
        }

        val allTagIds = links.mapNotNull { it.tagId }.distinct()
        if (allTagIds.isEmpty()) {
            return emptyMap()
        }

        val allParentTags = queryList(RealmTag::class.java) {
            `in`("id", allTagIds.toTypedArray())
        }
        val parentTagsById = allParentTags.associateBy { it.id }

        val tagsByLinkId = mutableMapOf<String, MutableList<RealmTag>>()
        links.forEach { link ->
            link.linkId?.let { linkId ->
                link.tagId?.let { tagId ->
                    parentTagsById[tagId]?.let { parentTag ->
                        tagsByLinkId.getOrPut(linkId) { mutableListOf() }.add(parentTag)
                    }
                }
            }
        }

        return tagsByLinkId
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
