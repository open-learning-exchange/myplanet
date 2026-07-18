package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import java.util.Calendar
import java.util.UUID
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.NetworkUtils

/**
 * Room replacement for the former Realm `RealmMyLibrary` model (resources).
 *
 * The multi-valued primitive fields (`userId`, `resourceFor`, `subject`, `level`, `tag`,
 * `languages`, formerly `RealmList<String>`) become `List<String>?` stored as JSON via the shared
 * [org.ole.planet.myplanet.data.room.Converters]. `attachments` (formerly
 * `RealmList<RealmAttachment>`) — a value-object child never queried on its own — becomes an
 * embedded JSON `List<RealmAttachment>`. Shelf membership (`userId` list containment) is queried
 * with `LIKE` on the JSON column (see `MyLibraryDao`). The class name is kept so the wide resources
 * UI/repo surface is untouched; a later rename pass drops the `Realm` prefix. Persistence goes
 * through [org.ole.planet.myplanet.data.room.dao.MyLibraryDao].
 */
@Entity(tableName = "my_library", indices = [Index("_rev"), Index("titleNormal")])
open class RealmMyLibrary {
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
    var attachments: List<RealmAttachment>? = null

    fun serializeResource(): JsonObject {
        return JsonObject().apply {
            addProperty("_id", _id)
            addProperty("_rev", _rev)
            addProperty("need_optimization", needsOptimization)
            add("resourceFor", resourceFor.toJsonArray())
            addProperty("publisher", publisher)
            addProperty("linkToLicense", linkToLicense)
            addProperty("addedBy", addedBy)
            addProperty("uploadDate", uploadDate)
            addProperty("openWith", openWith)
            add("subject", subject.toJsonArray())
            addProperty("kind", kind)
            addProperty("medium", medium)
            addProperty("language", language)
            addProperty("author", author)
            addProperty("sum", sum)
            addProperty("createdDate", uploadDate)
            add("level", level.toJsonArray())
            add("languages", languages.toJsonArray())
            add("tag", tag.toJsonArray())
            addProperty("timesRated", timesRated)
            addProperty("year", year)
            addProperty("title", title)
            addProperty("averageRating", averageRating)
            addProperty("filename", filename)
            addProperty("mediaType", mediaType)
            addProperty("description", description)
            val ob = JsonObject()
            resourceLocalAddress?.let { addr ->
                ob.add(addr, JsonObject())
            }
            add("_attachments", ob)
        }
    }

    private fun List<String>?.toJsonArray(): JsonArray {
        return JsonArray().apply {
            this@toJsonArray?.forEach { add(it) }
        }
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

    fun needToUpdate(): Boolean {
        return !resourceOffline || resourceLocalAddress != null && _rev != downloadedRev
    }

    companion object {
        fun serialize(personal: RealmMyLibrary, user: RealmUser?): JsonObject {
            return JsonObject().apply {
                addProperty("title", personal.title)
                addProperty("uploadDate", System.currentTimeMillis())
                addProperty("createdDate", personal.createdDate)
                addProperty("filename", FileUtils.getFileNameFromUrl(personal.resourceLocalAddress))
                addProperty("author", personal.author ?: "")
                addProperty("addedBy", user?.id)
                addProperty("medium", personal.medium)
                addProperty("description", personal.description)
                addProperty("year", personal.year)
                addProperty("language", personal.language)
                addProperty("publisher", personal.publisher ?: "")
                addProperty("linkToLicense", personal.linkToLicense ?: "")
                add("subject", JsonUtils.getAsJsonArray(personal.subject))
                add("level", JsonUtils.getAsJsonArray(personal.level))
                addProperty("resourceType", personal.resourceType)
                addProperty("openWith", personal.openWith)
                addProperty("mediaType", personal.mediaType ?: "other")
                add("resourceFor", JsonUtils.getAsJsonArray(personal.resourceFor))
                addProperty("private", personal.isPrivate)
                if (personal.isPrivate && personal.privateFor != null) {
                    val privateForObj = JsonObject()
                    privateForObj.addProperty("teams", personal.privateFor)
                    add("privateFor", privateForObj)
                }
                addProperty("isDownloadable", true)
                addProperty("sourcePlanet", user?.planetCode)
                addProperty("resideOn", user?.planetCode)
                addProperty("updatedDate", Calendar.getInstance().timeInMillis)
                addProperty("createdDate", personal.createdDate)
                addProperty("androidId", NetworkUtils.getUniqueIdentifier())
                addProperty("deviceName", NetworkUtils.getDeviceName())
                addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))
            }
        }

        data class InsertParams(
            val doc: JsonObject,
            val spm: SharedPrefManager,
            val userId: String? = "",
            val stepId: String? = "",
            val courseId: String? = "",
            val existing: RealmMyLibrary? = null
        )

        private fun JsonArray?.mergeInto(target: MutableList<String>) {
            this?.forEach { jsonElement ->
                val value = jsonElement.takeIf { it !is JsonNull }?.asString ?: return@forEach
                if (value !in target) {
                    target.add(value)
                }
            }
        }

        private fun mergedList(current: List<String>?, array: JsonArray?): List<String> {
            val target = current?.toMutableList() ?: mutableListOf()
            array.mergeInto(target)
            return target
        }

        /**
         * Builds/updates an unmanaged [RealmMyLibrary] from a CouchDB doc, merging into
         * [InsertParams.existing] when supplied (mirrors the former Realm find-or-create logic).
         */
        fun insertMyLibrary(params: InsertParams): RealmMyLibrary? {
            if (params.doc.entrySet().isEmpty()) return null
            val resourceId = JsonUtils.getString("_id", params.doc)
            val resource = params.existing ?: RealmMyLibrary().apply { id = resourceId }
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
                _rev = JsonUtils.getString("_rev", params.doc)
                this.resourceId = resourceId
                val titleString = JsonUtils.getString("title", params.doc)
                title = titleString
                titleNormal = titleString.let {
                    java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFD)
                        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                        .lowercase(java.util.Locale.ROOT)
                }
                description = JsonUtils.getString("description", params.doc)
                if (params.doc.has("_attachments")) {
                    val attachmentsObj = params.doc["_attachments"].asJsonObject
                    val attachmentList = this.attachments?.toMutableList() ?: mutableListOf()

                    attachmentsObj.entrySet().forEach { (key, attachmentValue) ->
                        val attachmentObj = attachmentValue.asJsonObject
                        val realmAttachment = RealmAttachment().apply {
                            id = UUID.randomUUID().toString()
                            name = key
                            contentType = attachmentObj.get("content_type")?.asString
                            length = attachmentObj.get("length")?.asLong ?: 0
                            digest = attachmentObj.get("digest")?.asString
                            isStub = attachmentObj.get("stub")?.asBoolean == true
                            revpos = attachmentObj.get("revpos")?.asInt ?: 0
                        }
                        attachmentList.add(realmAttachment)

                        if (key.indexOf("/") < 0) {
                            resourceRemoteAddress = "${params.spm.getCouchdbUrl().ifEmpty { "http://" }}/resources/$resourceId/$key"
                            resourceLocalAddress = key
                            resourceOffline = FileUtils.checkFileExist(context, resourceRemoteAddress)
                            if (resourceOffline) {
                                downloadedRev = JsonUtils.getString("_rev", params.doc)
                            }
                        }
                    }
                    this.attachments = attachmentList
                }
                filename = JsonUtils.getString("filename", params.doc)
                averageRating = JsonUtils.getString("averageRating", params.doc)
                uploadDate = JsonUtils.getString("uploadDate", params.doc)
                year = JsonUtils.getString("year", params.doc)
                addedBy = JsonUtils.getString("addedBy", params.doc)
                publisher = JsonUtils.getString("publisher", params.doc)
                linkToLicense = JsonUtils.getString("linkToLicense", params.doc)
                openWith = JsonUtils.getString("openWith", params.doc)
                articleDate = JsonUtils.getString("articleDate", params.doc)
                kind = JsonUtils.getString("kind", params.doc)
                createdDate = JsonUtils.getLong("createdDate", params.doc)
                language = JsonUtils.getString("language", params.doc)
                author = JsonUtils.getString("author", params.doc)
                mediaType = JsonUtils.getString("mediaType", params.doc)
                resourceType = JsonUtils.getString("resourceType", params.doc)
                timesRated = JsonUtils.getInt("timesRated", params.doc)
                medium = JsonUtils.getString("medium", params.doc)
                resourceFor = mergedList(resourceFor, JsonUtils.getJsonArray("resourceFor", params.doc))
                subject = mergedList(subject, JsonUtils.getJsonArray("subject", params.doc))
                level = mergedList(level, JsonUtils.getJsonArray("level", params.doc))
                tag = mergedList(tag, JsonUtils.getJsonArray("tags", params.doc))
                if (!isLocalOnlyPrivate) {
                    isPrivate = JsonUtils.getBoolean("private", params.doc)
                    if (isPrivate && params.doc.has("privateFor")) {
                        val privateForElement = params.doc.get("privateFor")
                        if (privateForElement.isJsonObject) {
                            privateFor = privateForElement.asJsonObject.get("teams")?.asString
                        }
                    }
                }
                languages = mergedList(languages, JsonUtils.getJsonArray("languages", params.doc))
            }
            return resource
        }

        fun listToString(list: List<String>?): String {
            return list?.joinToString(", ") ?: ""
        }
    }
}

/**
 * Value-object attachment embedded (as JSON) in [RealmMyLibrary]. Never persisted or queried on
 * its own, so it is a plain class rather than a Room entity.
 */
open class RealmAttachment {
    var id: String? = null
    var name: String? = null
    var contentType: String? = null
    var length: Long = 0
    var digest: String? = null
    var isStub: Boolean = false
    var revpos: Int = 0
}
