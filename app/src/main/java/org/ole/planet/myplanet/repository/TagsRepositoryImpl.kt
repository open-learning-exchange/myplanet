package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import javax.inject.Inject
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.utils.JsonUtils

class TagsRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), TagsRepository {

    override suspend fun insertTagsList(docs: List<JsonObject>) {
        executeTransaction { mRealm ->
            docs.forEach { act ->
                insertTagToRealm(mRealm, act)
            }
        }
    }

    override suspend fun insertFromJson(act: JsonObject) {
        executeTransaction { mRealm ->
            insertTagToRealm(mRealm, act)
        }
    }

    private fun insertTagToRealm(mRealm: Realm, act: JsonObject) {
        var tag = mRealm.where(RealmTag::class.java).equalTo("_id", JsonUtils.getString("_id", act)).findFirst()
        if (tag == null) {
            tag = mRealm.createObject(RealmTag::class.java, JsonUtils.getString("_id", act))
        }
        if (tag != null) {
            tag._rev = JsonUtils.getString("_rev", act)
            tag._id = JsonUtils.getString("_id", act)
            tag.name = JsonUtils.getString("name", act)
            tag.db = JsonUtils.getString("db", act)
            tag.docType = JsonUtils.getString("docType", act)
            tag.tagId = JsonUtils.getString("tagId", act)
            tag.linkId = JsonUtils.getString("linkId", act)
            val el = act["attachedTo"]
            if (el != null && el.isJsonArray) {
                val attachedTo = JsonUtils.getJsonArray("attachedTo", act)
                tag.attachedTo?.clear()
                for (i in 0 until attachedTo.size()) {
                    tag.attachedTo?.add(JsonUtils.getString(attachedTo, i))
                }
            } else {
                val attachedStr = JsonUtils.getString("attachedTo", act)
                if (attachedStr.isNotEmpty()) {
                    tag.attachedTo?.clear()
                    tag.attachedTo?.add(attachedStr)
                }
            }
            tag.isAttached = (tag.attachedTo?.size ?: 0) > 0
        }
    }

    override fun getTagsArray(tags: List<RealmTag>): JsonArray {
        val array = JsonArray()
        for (t in tags) {
            array.add(t._id)
        }
        return array
    }

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
