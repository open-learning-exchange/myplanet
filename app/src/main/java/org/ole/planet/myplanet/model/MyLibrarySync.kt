package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import java.util.UUID
import kotlinx.serialization.json.JsonArray as KJsonArray
import kotlinx.serialization.json.JsonNull as KJsonNull
import kotlinx.serialization.json.JsonObject as KJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.AppStorage
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.toKotlinx

// App-side sync helpers for MyLibrary (CouchDB doc -> entity), kept out of the Room entity.

/** Inputs for [insertMyLibrary]; formerly the nested `MyLibrary.Companion.InsertParams`. */
data class MyLibraryInsertParams(
    val doc: JsonObject,
    val spm: SharedPrefManager,
    val storage: AppStorage,
    val userId: String? = "",
    val stepId: String? = "",
    val courseId: String? = "",
    val existing: MyLibrary? = null
)

private fun KJsonArray?.mergeInto(target: MutableList<String>) {
    this?.forEach { jsonElement ->
        val value = (jsonElement as? JsonPrimitive)?.takeIf { it != KJsonNull }?.content ?: return@forEach
        if (value !in target) {
            target.add(value)
        }
    }
}

private fun mergedList(current: List<String>?, array: KJsonArray?): List<String> {
    val target = current?.toMutableList() ?: mutableListOf()
    array.mergeInto(target)
    return target
}

/**
 * Builds/updates an unmanaged [MyLibrary] from a CouchDB doc, merging into
 * [MyLibraryInsertParams.existing] when supplied (mirrors the former find-or-create logic).
 */
fun MyLibrary.Companion.insertMyLibrary(params: MyLibraryInsertParams): MyLibrary? {
    val kDoc = params.doc.toKotlinx().jsonObject
    if (kDoc.isEmpty()) return null
    val resourceId = JsonUtils.getString("_id", kDoc)
    val resource = params.existing ?: MyLibrary().apply { id = resourceId }
    val wasPrivate = params.existing?.isPrivate == true
    val hadPrivateFor = params.existing?.privateFor
    val hadRev = params.existing?._rev
    val isLocalOnlyPrivate = hadRev.isNullOrBlank() && wasPrivate && !hadPrivateFor.isNullOrBlank()

    resource.apply {
        setUserId(params.userId)
        _id = resourceId
        if (!params.stepId.isNullOrBlank()) {
            this.stepId = params.stepId
        }
        if (!params.courseId.isNullOrBlank()) {
            this.courseId = params.courseId
        }
        _rev = JsonUtils.getString("_rev", kDoc)
        this.resourceId = resourceId
        val titleString = JsonUtils.getString("title", kDoc)
        title = titleString
        titleNormal = Utilities.normalizeText(titleString)
        description = JsonUtils.getString("description", kDoc)
        if (kDoc.containsKey("_attachments")) {
            val attachmentsObj = kDoc.getValue("_attachments").jsonObject
            val attachmentList = this.attachments?.toMutableList() ?: mutableListOf()
            val existingNames = attachmentList.mapNotNullTo(mutableSetOf()) { it.name }
            val couchdbUrl = params.spm.getCouchdbUrl().ifEmpty { "http://" }

            attachmentsObj.entries.forEach { (key, attachmentValue) ->
                if (key !in existingNames) {
                    val attachmentObj = attachmentValue.jsonObject
                    val realmAttachment = Attachment().apply {
                        id = UUID.randomUUID().toString()
                        name = key
                        contentType = JsonUtils.rawString("content_type", attachmentObj)
                        length = JsonUtils.rawLong("length", attachmentObj) ?: 0
                        digest = JsonUtils.rawString("digest", attachmentObj)
                        isStub = JsonUtils.rawBoolean("stub", attachmentObj) == true
                        revpos = JsonUtils.rawInt("revpos", attachmentObj) ?: 0
                    }
                    attachmentList.add(realmAttachment)
                    existingNames.add(key)
                }

                if (key.indexOf("/") < 0) {
                    resourceRemoteAddress = "$couchdbUrl/resources/$resourceId/$key"
                    resourceLocalAddress = key
                    resourceOffline = params.storage.hasDownloadedFile(resourceRemoteAddress)
                    if (resourceOffline) {
                        downloadedRev = JsonUtils.getString("_rev", kDoc)
                    }
                }
            }
            this.attachments = attachmentList
        }
        filename = JsonUtils.getString("filename", kDoc)
        averageRating = JsonUtils.getString("averageRating", kDoc)
        uploadDate = JsonUtils.getString("uploadDate", kDoc)
        year = JsonUtils.getString("year", kDoc)
        addedBy = JsonUtils.getString("addedBy", kDoc)
        publisher = JsonUtils.getString("publisher", kDoc)
        linkToLicense = JsonUtils.getString("linkToLicense", kDoc)
        openWith = JsonUtils.getString("openWith", kDoc)
        openWhichFile = JsonUtils.getString("openWhichFile", kDoc).takeIf { it.isNotBlank() }
        articleDate = JsonUtils.getString("articleDate", kDoc)
        kind = JsonUtils.getString("kind", kDoc)
        createdDate = JsonUtils.getLong("createdDate", kDoc)
        language = JsonUtils.getString("language", kDoc)
        author = JsonUtils.getString("author", kDoc)
        mediaType = JsonUtils.getString("mediaType", kDoc)
        resourceType = JsonUtils.getString("resourceType", kDoc)
        timesRated = JsonUtils.getInt("timesRated", kDoc)
        medium = JsonUtils.getString("medium", kDoc)
        resourceFor = mergedList(resourceFor, JsonUtils.getJsonArray("resourceFor", kDoc))
        subject = mergedList(subject, JsonUtils.getJsonArray("subject", kDoc))
        level = mergedList(level, JsonUtils.getJsonArray("level", kDoc))
        tag = mergedList(tag, JsonUtils.getJsonArray("tags", kDoc))
        if (!isLocalOnlyPrivate) {
            isPrivate = JsonUtils.getBoolean("private", kDoc)
            if (isPrivate && kDoc.containsKey("privateFor")) {
                val privateForElement = kDoc["privateFor"]
                if (privateForElement is KJsonObject) {
                    privateFor = JsonUtils.rawString("teams", privateForElement)
                }
            }
        }
        languages = mergedList(languages, JsonUtils.getJsonArray("languages", kDoc))
    }
    return resource
}
