package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import javax.inject.Inject
import org.ole.planet.myplanet.data.room.dao.TagDao
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.utils.JsonUtils

class TagsRepositoryImpl @Inject constructor(
    private val tagDao: TagDao,
) : TagsRepository {

    override suspend fun getTags(dbType: String?): List<RealmTag> {
        return tagDao.getParentTags(dbType)
    }

    override suspend fun getTagsWithChildren(dbType: String?): Map<RealmTag, List<RealmTag>> {
        val parentTags = getTags(dbType)
        val allTags = tagDao.getAll()
        val childMap = mutableMapOf<String, MutableList<RealmTag>>()

        for (t in allTags) {
            val attached = t.attachedTo
            if (attached.isNullOrEmpty()) continue

            for (parentId in attached) {
                if (parentId != null) {
                    val list = childMap.getOrPut(parentId) { ArrayList() }
                    if (list.isEmpty() || list.last() !== t) {
                        list.add(t)
                    }
                }
            }
        }

        return parentTags.associateWith { parent ->
            childMap[parent.id] ?: emptyList()
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

    override suspend fun getTagsForCourses(courseIds: List<String>): Map<String, List<RealmTag>> {
        return getLinkedTagsBulk("courses", courseIds)
    }

    private suspend fun getLinkedTagsBulk(db: String, linkIds: List<String>): Map<String, List<RealmTag>> {
        if (linkIds.isEmpty()) {
            return emptyMap()
        }

        val links = tagDao.getByDbAndLinkIds(db, linkIds)
        if (links.isEmpty()) {
            return emptyMap()
        }

        val allTagIds = links.mapNotNull { it.tagId }.distinct()
        if (allTagIds.isEmpty()) {
            return emptyMap()
        }

        val parentTagsById = tagDao.getByIds(allTagIds).associateBy { it.id }

        val tagsByLinkId = mutableMapOf<String, MutableList<RealmTag>>()
        val tagsSetByLinkId = mutableMapOf<String, MutableSet<String>>()
        links.forEach { link ->
            link.linkId?.let { linkId ->
                link.tagId?.let { tagId ->
                    parentTagsById[tagId]?.let { parentTag ->
                        val set = tagsSetByLinkId.getOrPut(linkId) { mutableSetOf() }
                        if (set.add(parentTag.id)) {
                            tagsByLinkId.getOrPut(linkId) { mutableListOf() }.add(parentTag)
                        }
                    }
                }
            }
        }

        return tagsByLinkId
    }

    private suspend fun getLinkedTags(db: String, linkId: String): List<RealmTag> {
        val links = tagDao.getByDbAndLinkId(db, linkId)
        if (links.isEmpty()) {
            return emptyList()
        }
        val tagIds = links.mapNotNull { it.tagId }.distinct()
        if (tagIds.isEmpty()) {
            return emptyList()
        }

        val parents = tagDao.getByIds(tagIds)
        if (parents.isEmpty()) {
            return emptyList()
        }

        val parentsById = parents.associateBy { it.id }
        return tagIds.mapNotNull { parentsById[it] }
    }

    override suspend fun insert(documentList: List<JsonObject>) {
        if (documentList.isEmpty()) return
        val tagsToInsert = documentList
            .filter { !JsonUtils.getString("_id", it).startsWith("_design") }
            .map { createUnmanagedTag(it) }
        if (tagsToInsert.isNotEmpty()) {
            tagDao.upsertAll(tagsToInsert)
        }
    }

    private fun createUnmanagedTag(act: JsonObject): RealmTag {
        val tag = RealmTag()
        tag.id = JsonUtils.getString("_id", act)
        tag._rev = JsonUtils.getString("_rev", act)
        tag._id = JsonUtils.getString("_id", act)
        tag.name = JsonUtils.getString("name", act)
        tag.db = JsonUtils.getString("db", act)
        tag.docType = JsonUtils.getString("docType", act)
        tag.tagId = JsonUtils.getString("tagId", act)
        tag.linkId = JsonUtils.getString("linkId", act)
        val el = act["attachedTo"]
        val attachedTo = ArrayList<String>()
        if (el != null && el.isJsonArray) {
            val arr = JsonUtils.getJsonArray("attachedTo", act)
            for (i in 0 until arr.size()) {
                attachedTo.add(JsonUtils.getString(arr, i))
            }
        } else {
            attachedTo.add(JsonUtils.getString("attachedTo", act))
        }
        tag.attachedTo = attachedTo
        tag.isAttached = attachedTo.size > 0
        return tag
    }
}
