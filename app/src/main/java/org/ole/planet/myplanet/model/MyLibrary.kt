package org.ole.planet.myplanet.model

import android.content.Context
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx
import kotlinx.serialization.json.JsonArray as KJsonArray
import kotlinx.serialization.json.JsonNull as KJsonNull
import kotlinx.serialization.json.JsonObject as KJsonObject

/**
 * Room replacement for the former `MyLibrary` model (resources).
 *
 * The multi-valued primitive fields (`userId`, `resourceFor`, `subject`, `level`, `tag`,
 * `languages`, formerly `RealmList<String>`) become `List<String>?` stored as JSON via the shared
 * [org.ole.planet.myplanet.data.room.Converters]. `attachments` (formerly
 * `RealmList<Attachment>`) — a value-object child never queried on its own — becomes an
 * embedded JSON `List<Attachment>`. Shelf membership (`userId` list containment) is queried
 * with `LIKE` on the JSON column (see `MyLibraryDao`). The class name is kept so the wide resources
 * UI/repo surface is untouched. Persistence goes
 * through [org.ole.planet.myplanet.data.room.dao.MyLibraryDao].
 */
@Entity(
    tableName = "my_library",
    indices = [
        Index("_rev"),
        Index("titleNormal"),
        Index("resourceId"),
        Index("isPrivate")
    ]
)
open class MyLibrary {
    @PrimaryKey
    @JvmField
    var id: String = ""
    @JvmField
    var _id: String? = null
    var userId: List<String>? = null
    var resourceRemoteAddress: String? = null
    var resourceLocalAddress: String? = null
    var resourceOffline: Boolean = false
    var resourceId: String? = null
    var _rev: String? = null
    var downloadedRev: String? = null
    var needsOptimization: Boolean = false
    var publisher: String? = null
    var linkToLicense: String? = null
    var addedBy: String? = null
    var uploadDate: String? = null
    var createdDate: Long = 0
    var openWith: String? = null
    var openWhichFile: String? = null
    var articleDate: String? = null
    var kind: String? = null
    var language: String? = null
    var author: String? = null
    var year: String? = null
    var medium: String? = null
    var title: String? = null
    var titleNormal: String? = null
    var averageRating: String? = null
    var filename: String? = null
    var mediaType: String? = null
    var resourceType: String? = null
    var description: String? = null
    var translationAudioPath: String? = null
    var sum: Int = 0
    var timesRated: Int = 0
    var resourceFor: List<String>? = null
    var subject: List<String>? = null
    var level: List<String>? = null
    var tag: List<String>? = null
    var languages: List<String>? = null
    var courseId: String? = null
    var stepId: String? = null
    var isPrivate: Boolean = false
    var privateFor: String? = null
    var attachments: List<Attachment>? = null

    fun serializeResource(): JsonObject = buildJsonObject {
        put("_id", _id)
        put("_rev", _rev)
        put("need_optimization", needsOptimization)
        put("resourceFor", resourceFor.toJsonArray())
        put("publisher", publisher)
        put("linkToLicense", linkToLicense)
        put("addedBy", addedBy)
        put("uploadDate", uploadDate)
        put("openWith", openWith)
        put("subject", subject.toJsonArray())
        put("kind", kind)
        put("medium", medium)
        put("language", language)
        put("author", author)
        put("sum", sum)
        put("createdDate", uploadDate)
        put("level", level.toJsonArray())
        put("languages", languages.toJsonArray())
        put("tag", tag.toJsonArray())
        put("timesRated", timesRated)
        put("year", year)
        put("title", title)
        put("averageRating", averageRating)
        put("filename", filename)
        put("mediaType", mediaType)
        put("description", description)
        put("_attachments", buildJsonObject {
            resourceLocalAddress?.let { addr ->
                put(addr, buildJsonObject { })
            }
        })
    }.toGson()

    private fun List<String>?.toJsonArray() = buildJsonArray {
        this@toJsonArray?.forEach { add(it) }
    }

    fun setUserId(userId: String?) {
        if (userId.isNullOrBlank()) return
        val current = this.userId?.toMutableList() ?: mutableListOf()
        if (!current.contains(userId)) {
            current.add(userId)
        }
        this.userId = current
    }

    @Ignore
    fun isResourceOffline(): Boolean {
        return resourceOffline && _rev == downloadedRev
    }

    @get:Ignore
    val subjectsAsString: String
        get() = subject?.joinToString(", ") ?: ""

    override fun toString(): String {
        return title ?: ""
    }

    fun removeUserId(id: String?) {
        this.userId = this.userId?.filterNot { it == id }
    }

    companion object {
        data class InsertParams(
            val doc: JsonObject,
            val spm: SharedPrefManager,
            val context: Context,
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
         * [InsertParams.existing] when supplied (mirrors the former find-or-create logic).
         */
        fun insertMyLibrary(params: InsertParams): MyLibrary? {
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
                            resourceOffline = FileUtils.checkFileExist(params.context, resourceRemoteAddress)
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

        fun listToString(list: List<String>?): String {
            return list?.joinToString(", ") ?: ""
        }
    }
}

/**
 * Value-object attachment embedded (as JSON) in [MyLibrary]. Never persisted or queried on
 * its own, so it is a plain class rather than a Room entity.
 */
@Serializable
open class Attachment {
    var id: String? = null
    var name: String? = null
    var contentType: String? = null
    var length: Long = 0
    var digest: String? = null
    var isStub: Boolean = false
    var revpos: Int = 0
}
