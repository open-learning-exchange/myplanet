package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import java.util.Calendar
import java.util.UUID
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.NetworkUtils

open class RealmMyLibrary : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var _id: String? = null
    var userId: RealmList<String>? = null
        private set
    var resourceRemoteAddress: String? = null
    var resourceLocalAddress: String? = null
    var resourceOffline: Boolean = false
    var resourceId: String? = null
    @Index
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
    var averageRating: String? = null
    var filename: String? = null
    var mediaType: String? = null
    var resourceType: String? = null
    var description: String? = null
    var translationAudioPath: String? = null
    var sum: Int = 0
    var timesRated: Int = 0
    var resourceFor: RealmList<String>? = null
    var subject: RealmList<String>? = null
    var level: RealmList<String>? = null
    var tag: RealmList<String>? = null
    var languages: RealmList<String>? = null
    var courseId: String? = null
    var stepId: String? = null
    var isPrivate: Boolean = false
    var privateFor: String? = null
    var attachments: RealmList<RealmAttachment>? = null

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
    private fun RealmList<String>?.toJsonArray(): JsonArray {
        return JsonArray().apply {
            this@toJsonArray?.forEach { add(it) }
        }
    }
    fun setUserId(userId: String?, realm: Realm? = null) {
        if (userId.isNullOrBlank()) return

        val executeInTransaction = realm != null && !realm.isInTransaction
        
        if (executeInTransaction) {
            realm.beginTransaction()
        }
        
        try {
            if (this.userId == null) {
                this.userId = RealmList()
            }
            if (this.userId?.contains(userId) == false) {
                this.userId?.add(userId)
            }
            
            if (executeInTransaction) {
                realm.commitTransaction()
            }
        } catch (e: Exception) {
            if (executeInTransaction && realm.isInTransaction) {
                realm.cancelTransaction()
            }
            throw e
        }
    }
    fun isResourceOffline(): Boolean {
        return resourceOffline && _rev == downloadedRev
    }
    private fun JsonArray?.setListIfNotNull(targetList: RealmList<String>?, setter: (String) -> Unit) {
        this?.forEach { jsonElement ->
            val value = jsonElement.takeIf { it !is JsonNull }?.asString ?: return@forEach
            if (value !in targetList.orEmpty()) {
                setter(value)
            }
        }
    }

    fun setResourceFor(array: JsonArray, resource: RealmMyLibrary?) {
        array.setListIfNotNull(resource?.resourceFor) { resource?.resourceFor?.add(it) }
    }

    fun setSubject(array: JsonArray, resource: RealmMyLibrary?) {
        array.setListIfNotNull(resource?.subject) { resource?.subject?.add(it) }
    }

    fun setLevel(array: JsonArray, resource: RealmMyLibrary?) {
        array.setListIfNotNull(resource?.level) { resource?.level?.add(it) }
    }

    fun setTag(array: JsonArray, resource: RealmMyLibrary?) {
        array.setListIfNotNull(resource?.tag) { resource?.tag?.add(it) }
    }

    fun setLanguages(array: JsonArray, resource: RealmMyLibrary?) {
        array.setListIfNotNull(resource?.languages) { resource?.languages?.add(it) }
    }

    fun setUserId(userId: RealmList<String>?) {
        this.userId = userId
    }

    val subjectsAsString: String
        get() = subject?.joinToString(", ") ?: ""

    override fun toString(): String {
        return title ?: ""
    }

    fun removeUserId(id: String?) {
        userId?.remove(id)
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
            val mRealm: Realm?,
            val spm: org.ole.planet.myplanet.services.SharedPrefManager,
            val userId: String? = "",
            val stepId: String? = "",
            val courseId: String? = ""
        )

        fun insertMyLibrary(params: InsertParams): RealmMyLibrary? {
            if (params.doc.entrySet().isEmpty()) return null
            val resourceId = JsonUtils.getString("_id", params.doc)
            var resource = params.mRealm?.where(RealmMyLibrary::class.java)?.equalTo("id", resourceId)?.findFirst()
            val wasPrivate = resource?.isPrivate == true
            val hadPrivateFor = resource?.privateFor
            val hadRev = resource?._rev
            val isLocalOnlyPrivate = hadRev.isNullOrBlank() && wasPrivate && !hadPrivateFor.isNullOrBlank()
            if (resource == null) {
                resource = if (params.mRealm != null) {
                    params.mRealm.createObject(RealmMyLibrary::class.java, resourceId)
                } else {
                    RealmMyLibrary().apply { id = resourceId }
                }
            }
            resource?.apply {
                if (this.userId == null) this.userId = RealmList()
                setUserId(params.userId, params.mRealm)
                _id = resourceId
                if (!params.stepId.isNullOrBlank()) {
                    this.stepId = params.stepId
                }
                if (!params.courseId.isNullOrBlank()) {
                    this.courseId = params.courseId
                }
                _rev = JsonUtils.getString("_rev", params.doc)
                this.resourceId = resourceId
                title = JsonUtils.getString("title", params.doc)
                description = JsonUtils.getString("description", params.doc)
                if (params.doc.has("_attachments")) {
                    val attachments = params.doc["_attachments"].asJsonObject
                    if (this.attachments == null) {
                        this.attachments = RealmList()
                    }

                    attachments.entrySet().forEach { (key, attachmentValue) ->
                        val attachmentObj = attachmentValue.asJsonObject

                        val realmAttachment = if (params.mRealm != null) {
                            params.mRealm.createObject(RealmAttachment::class.java, UUID.randomUUID().toString())
                        } else {
                            RealmAttachment().apply { id = UUID.randomUUID().toString() }
                        }
                        realmAttachment.apply {
                            name = key
                            contentType = attachmentObj.get("content_type")?.asString
                            length = attachmentObj.get("length")?.asLong ?: 0
                            digest = attachmentObj.get("digest")?.asString
                            isStub = attachmentObj.get("stub")?.asBoolean == true
                            revpos = attachmentObj.get("revpos")?.asInt ?: 0
                        }

                        this.attachments?.add(realmAttachment)

                        if (key.indexOf("/") < 0) {
                            resourceRemoteAddress = "${params.spm.getCouchdbUrl().ifEmpty { "http://" }}/resources/$resourceId/$key"
                            resourceLocalAddress = key
                            resourceOffline = FileUtils.checkFileExist(context, resourceRemoteAddress)
                            if (resourceOffline) {
                                downloadedRev = JsonUtils.getString("_rev", params.doc)
                            }
                        }
                    }
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
                if (this.resourceFor == null) this.resourceFor = RealmList()
                setResourceFor(JsonUtils.getJsonArray("resourceFor", params.doc), this)
                if (this.subject == null) this.subject = RealmList()
                setSubject(JsonUtils.getJsonArray("subject", params.doc), this)
                if (this.level == null) this.level = RealmList()
                setLevel(JsonUtils.getJsonArray("level", params.doc), this)
                if (this.tag == null) this.tag = RealmList()
                setTag(JsonUtils.getJsonArray("tags", params.doc), this)
                if (!isLocalOnlyPrivate) {
                    isPrivate = JsonUtils.getBoolean("private", params.doc)
                    if (isPrivate && params.doc.has("privateFor")) {
                        val privateForElement = params.doc.get("privateFor")
                        if (privateForElement.isJsonObject) {
                            privateFor = privateForElement.asJsonObject.get("teams")?.asString
                        }
                    }
                }
                if (this.languages == null) this.languages = RealmList()
                setLanguages(JsonUtils.getJsonArray("languages", params.doc), this)
            }
            return resource
        }

        fun listToString(list: RealmList<String>?): String {
            return list?.joinToString(", ") ?: ""
        }

        fun save(allDocs: JsonArray, mRealm: Realm, spm: org.ole.planet.myplanet.services.SharedPrefManager): List<String> {
            val list: MutableList<String> = ArrayList()
            allDocs.forEach { doc ->
                val document = JsonUtils.getJsonObject("doc", doc.asJsonObject)
                val id = JsonUtils.getString("_id", document)
                if (!id.startsWith("_design")) {
                    list.add(id)
                    insertMyLibrary(InsertParams(doc = document, mRealm = mRealm, spm = spm))
                }
            }
            return list
        }
    }
}

open class RealmAttachment : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var name: String? = null
    var contentType: String? = null
    var length: Long = 0
    var digest: String? = null
    var isStub: Boolean = false
    var revpos: Int = 0
}
