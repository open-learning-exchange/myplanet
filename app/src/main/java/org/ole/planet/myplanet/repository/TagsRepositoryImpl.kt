package org.ole.planet.myplanet.repository

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.model.RealmTag

class TagsRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher
) : RealmRepository(databaseService, realmDispatcher), TagsRepository {

    override suspend fun getTags(dbType: String?): List<RealmTag> {
        return queryList(RealmTag::class.java) {
            dbType?.let { equalTo("db", it) }
            isNotEmpty("name")
            equalTo("isAttached", false)
        }
    }

    override suspend fun getTagsWithChildren(dbType: String?): Map<RealmTag, List<RealmTag>> {
        val parentTags = getTags(dbType)
        val allTags = queryList(RealmTag::class.java)
        val childMap = mutableMapOf<String, MutableList<RealmTag>>()
        val seenParents = HashSet<String>()

        allTags.forEach { t ->
            seenParents.clear()
            t.attachedTo?.forEach { parentId ->
                if (seenParents.add(parentId)) {
                    val list = childMap.getOrPut(parentId) { mutableListOf() }
                    list.add(t)
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
                        val list = tagsByLinkId.getOrPut(linkId) { mutableListOf() }
                        if (list.none { it.id == parentTag.id }) {
                            list.add(parentTag)
                        }
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

    override fun bulkInsertFromSync(realm: io.realm.Realm, jsonArray: com.google.gson.JsonArray) {
        val tagsToInsert = ArrayList<RealmTag>(jsonArray.size())
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = org.ole.planet.myplanet.utils.JsonUtils.getJsonObject("doc", jsonDoc)
            val id = org.ole.planet.myplanet.utils.JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                tagsToInsert.add(createUnmanagedTag(jsonDoc))
            }
        }
        if (tagsToInsert.isNotEmpty()) {
            realm.insertOrUpdate(tagsToInsert)
        }
    }

    override suspend fun insert(documentList: List<com.google.gson.JsonObject>) {
        if (documentList.isEmpty()) return
        executeTransaction { realm ->
            val tagsToInsert = documentList.map { createUnmanagedTag(it) }
            if (tagsToInsert.isNotEmpty()) {
                realm.insertOrUpdate(tagsToInsert)
            }
        }
    }

    private fun createUnmanagedTag(act: com.google.gson.JsonObject): RealmTag {
        val tag = RealmTag()
        tag.id = org.ole.planet.myplanet.utils.JsonUtils.getString("_id", act)
        tag._rev = org.ole.planet.myplanet.utils.JsonUtils.getString("_rev", act)
        tag._id = org.ole.planet.myplanet.utils.JsonUtils.getString("_id", act)
        tag.name = org.ole.planet.myplanet.utils.JsonUtils.getString("name", act)
        tag.db = org.ole.planet.myplanet.utils.JsonUtils.getString("db", act)
        tag.docType = org.ole.planet.myplanet.utils.JsonUtils.getString("docType", act)
        tag.tagId = org.ole.planet.myplanet.utils.JsonUtils.getString("tagId", act)
        tag.linkId = org.ole.planet.myplanet.utils.JsonUtils.getString("linkId", act)
        val el = act["attachedTo"]
        if (el != null && el.isJsonArray) {
            val attachedTo = org.ole.planet.myplanet.utils.JsonUtils.getJsonArray("attachedTo", act)
            tag.attachedTo = io.realm.RealmList()
            for (i in 0 until attachedTo.size()) {
                tag.attachedTo?.add(org.ole.planet.myplanet.utils.JsonUtils.getString(attachedTo, i))
            }
        } else {
            tag.attachedTo = io.realm.RealmList()
            tag.attachedTo?.add(org.ole.planet.myplanet.utils.JsonUtils.getString("attachedTo", act))
        }
        tag.isAttached = (tag.attachedTo?.size ?: 0) > 0
        return tag
    }
}
