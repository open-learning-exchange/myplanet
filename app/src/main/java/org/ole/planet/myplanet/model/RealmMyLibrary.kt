package org.ole.planet.myplanet.model

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.opencsv.CSVWriter
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.Calendar
import java.util.Date

open class RealmMyLibrary : RealmObject() {
    @JvmField
    @PrimaryKey
    var id: String? = null
    @JvmField
    var _id: String? = null
    var userId: RealmList<String>? = null
        private set
    @JvmField
    var resourceRemoteAddress: String? = null
    @JvmField
    var resourceLocalAddress: String? = null
    @JvmField
    var resourceOffline: Boolean = false
    @JvmField
    var resourceId: String? = null
    @JvmField
    var _rev: String? = null
    @JvmField
    var downloadedRev: String? = null
    @JvmField
    var needsOptimization: Boolean = false
    @JvmField
    var publisher: String? = null
    @JvmField
    var linkToLicense: String? = null
    @JvmField
    var addedBy: String? = null
    @JvmField
    var uploadDate: String? = null
    @JvmField
    var createdDate: Long = 0
    @JvmField
    var openWith: String? = null
    @JvmField
    var articleDate: String? = null
    @JvmField
    var kind: String? = null
    @JvmField
    var language: String? = null
    @JvmField
    var author: String? = null
    @JvmField
    var year: String? = null
    @JvmField
    var medium: String? = null
    @JvmField
    var title: String? = null
    @JvmField
    var averageRating: String? = null
    @JvmField
    var filename: String? = null
    @JvmField
    var mediaType: String? = null
    @JvmField
    var resourceType: String? = null
    @JvmField
    var description: String? = null
    @JvmField
    var translationAudioPath: String? = null
    @JvmField
    var sum: Int = 0
    @JvmField
    var timesRated: Int = 0
    @JvmField
    var resourceFor: RealmList<String>? = null
    @JvmField
    var subject: RealmList<String>? = null
    @JvmField
    var level: RealmList<String>? = null
    @JvmField
    var tag: RealmList<String>? = null
    @JvmField
    var languages: RealmList<String>? = null
    @JvmField
    var courseId: String? = null
    @JvmField
    var stepId: String? = null
    @JvmField
    var isPrivate: Boolean = false
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
            ob.add(resourceLocalAddress, JsonObject())
            add("_attachments", ob)
        }
    }
    private fun RealmList<String>?.toJsonArray(): JsonArray {
        return JsonArray().apply {
            this@toJsonArray?.forEach { add(it) }
        }
    }
    fun setUserId(userId: String?) {
        if (userId.isNullOrBlank()) return

        if (this.userId == null) {
            this.userId = RealmList()
        }
        if (!this.userId!!.contains(userId)) {
            this.userId?.add(userId)
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
        val libraryDataList: MutableList<Array<String>> = mutableListOf()

        fun getMyLibraryByUserId(mRealm: Realm, settings: SharedPreferences?): List<RealmMyLibrary> {
            val libs = mRealm.where(RealmMyLibrary::class.java).findAll()
            return getMyLibraryByUserId(settings?.getString("userId", "--"), libs, mRealm)
        }

        fun getMyLibraryByUserId(userId: String?, libs: List<RealmMyLibrary>, mRealm: Realm): List<RealmMyLibrary> {
            val ids = RealmMyTeam.getResourceIdsByUser(userId, mRealm)
            return libs.filter { it.userId?.contains(userId) == true || it.resourceId in ids }
        }

        @JvmStatic
        fun getMyLibraryByUserId(userId: String?, libs: List<RealmMyLibrary>): List<RealmMyLibrary> {
            return libs.filter { it.userId?.contains(userId) == true }
        }

        @JvmStatic
        fun getOurLibrary(userId: String?, libs: List<RealmMyLibrary>): List<RealmMyLibrary> {
            return libs.filter { it.userId?.contains(userId) == false }
        }

        private fun getIds(mRealm: Realm): Array<String?> {
            val list = mRealm.where(RealmMyLibrary::class.java).findAll()
            return list.map { it.resourceId }.toTypedArray()
        }

        @JvmStatic
        fun removeDeletedResource(newIds: List<String?>, mRealm: Realm) {
            val ids = getIds(mRealm)
            ids.filterNot { it in newIds }.forEach { id ->
                mRealm.executeTransaction { realm ->
                    realm.where(RealmMyLibrary::class.java).equalTo("resourceId", id).findAll().deleteAllFromRealm()
                }
            }
        }

        @JvmStatic
        fun serialize(personal: RealmMyLibrary, user: RealmUserModel?): JsonObject {
            return JsonObject().apply {
                addProperty("title", personal.title)
                addProperty("uploadDate", Date().time)
                addProperty("createdDate", personal.createdDate)
                addProperty("filename", FileUtils.getFileNameFromUrl(personal.resourceLocalAddress))
                addProperty("author", user?.name)
                addProperty("addedBy", user?.id)
                addProperty("medium", personal.medium)
                addProperty("description", personal.description)
                addProperty("year", personal.year)
                addProperty("language", personal.language)
                add("subject", JsonUtils.getAsJsonArray(personal.subject))
                add("level", JsonUtils.getAsJsonArray(personal.level))
                addProperty("resourceType", personal.resourceType)
                addProperty("openWith", personal.openWith)
                add("resourceFor", JsonUtils.getAsJsonArray(personal.resourceFor))
                addProperty("private", false)
                addProperty("isDownloadable", "")
                addProperty("sourcePlanet", user?.planetCode)
                addProperty("resideOn", user?.planetCode)
                addProperty("updatedDate", Calendar.getInstance().timeInMillis)
                addProperty("createdDate", personal.createdDate)
                addProperty("androidId", NetworkUtils.getUniqueIdentifier())
                addProperty("deviceName", NetworkUtils.getDeviceName())
                addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))
            }
        }

        private fun insertResources(doc: JsonObject, mRealm: Realm) {
            insertMyLibrary("", doc, mRealm)
        }

        @JvmStatic
        fun createStepResource(mRealm: Realm, res: JsonObject, myCoursesID: String?, stepId: String?) {
            insertMyLibrary("", stepId, myCoursesID, res, mRealm)
        }

        @JvmStatic
        fun insertMyLibrary(userId: String?, doc: JsonObject, mRealm: Realm) {
            if (!mRealm.isInTransaction) {
                mRealm.beginTransaction()
            }
            insertMyLibrary(userId, "", "", doc, mRealm)
            mRealm.commitTransaction()
        }

        @JvmStatic
        fun createFromResource(resource: RealmMyLibrary?, mRealm: Realm, userId: String?) {
            if (!mRealm.isInTransaction) {
                mRealm.beginTransaction()
            }
            resource?.setUserId(userId)
            mRealm.commitTransaction()
        }

        @JvmStatic
        fun insertMyLibrary(userId: String?, stepId: String?, courseId: String?, doc: JsonObject, mRealm: Realm) {
            if(mRealm.isInTransaction) {
                Log.e("RealmMyLibrary", "insertMyLibrary: Transaction is already in progress")
            } else{
                Log.e("RealmMyLibrary", "insertMyLibrary: Transaction is not in progress")
            }
            val resourceId = JsonUtils.getString("_id", doc)
            val settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var resource = mRealm.where(RealmMyLibrary::class.java).equalTo("id", resourceId).findFirst()
            if (resource == null) {
                resource = mRealm.createObject(RealmMyLibrary::class.java, resourceId)
            }
            resource?.apply {
                setUserId(userId)
                _id = resourceId
                if (!stepId.isNullOrBlank()) {
                    this.stepId = stepId
                }
                if (!courseId.isNullOrBlank()) {
                    this.courseId = courseId
                }
                _rev = JsonUtils.getString("_rev", doc)
                this.resourceId = resourceId
                title = JsonUtils.getString("title", doc)
                description = JsonUtils.getString("description", doc)
                if (doc.has("_attachments")) {
                    val attachments = doc["_attachments"].asJsonObject
                    attachments.entrySet().forEach { (key, _) ->
                        if (key.indexOf("/") < 0) {
                            resourceRemoteAddress = "${settings.getString("couchdbURL", "http://")}/resources/$resourceId/$key"
                            resourceLocalAddress = key
                            resourceOffline = FileUtils.checkFileExist(resourceRemoteAddress)
                        }
                    }
                }
                filename = JsonUtils.getString("filename", doc)
                averageRating = JsonUtils.getString("averageRating", doc)
                uploadDate = JsonUtils.getString("uploadDate", doc)
                year = JsonUtils.getString("year", doc)
                addedBy = JsonUtils.getString("addedBy", doc)
                publisher = JsonUtils.getString("publisher", doc)
                linkToLicense = JsonUtils.getString("linkToLicense", doc)
                openWith = JsonUtils.getString("openWith", doc)
                articleDate = JsonUtils.getString("articleDate", doc)
                kind = JsonUtils.getString("kind", doc)
                createdDate = JsonUtils.getLong("createdDate", doc)
                language = JsonUtils.getString("language", doc)
                author = JsonUtils.getString("author", doc)
                mediaType = JsonUtils.getString("mediaType", doc)
                resourceType = JsonUtils.getString("resourceType", doc)
                timesRated = JsonUtils.getInt("timesRated", doc)
                medium = JsonUtils.getString("medium", doc)
                setResourceFor(JsonUtils.getJsonArray("resourceFor", doc), this)
                setSubject(JsonUtils.getJsonArray("subject", doc), this)
                setLevel(JsonUtils.getJsonArray("level", doc), this)
                setTag(JsonUtils.getJsonArray("tags", doc), this)
                isPrivate = JsonUtils.getBoolean("private", doc)
                setLanguages(JsonUtils.getJsonArray("languages", doc), this)
            }
            mRealm.commitTransaction()

            val csvRow = arrayOf(
                JsonUtils.getString("_id", doc),
                JsonUtils.getString("_rev", doc),
                JsonUtils.getString("title", doc),
                JsonUtils.getString("description", doc),
                JsonUtils.getString("resourceRemoteAddress", doc),
                JsonUtils.getString("resourceLocalAddress", doc),
                JsonUtils.getBoolean("resourceOffline", doc).toString(),
                JsonUtils.getString("resourceId", doc),
                JsonUtils.getString("addedBy", doc),
                JsonUtils.getString("uploadDate", doc),
                JsonUtils.getLong("createdDate", doc).toString(),
                JsonUtils.getString("openWith", doc),
                JsonUtils.getString("articleDate", doc),
                JsonUtils.getString("kind", doc),
                JsonUtils.getString("language", doc),
                JsonUtils.getString("author", doc),
                JsonUtils.getString("year", doc),
                JsonUtils.getString("medium", doc),
                JsonUtils.getString("filename", doc),
                JsonUtils.getString("mediaType", doc),
                JsonUtils.getString("resourceType", doc),
                JsonUtils.getInt("timesRated", doc).toString(),
                JsonUtils.getString("averageRating", doc),
                JsonUtils.getString("publisher", doc),
                JsonUtils.getString("linkToLicense", doc),
                JsonUtils.getString("subject", doc),
                JsonUtils.getString("level", doc),
                JsonUtils.getString("tags", doc),
                JsonUtils.getString("languages", doc),
                JsonUtils.getString("courseId", doc),
                JsonUtils.getString("stepId", doc),
                JsonUtils.getString("downloaded", doc),
                JsonUtils.getBoolean("private", doc).toString(),
            )
            libraryDataList.add(csvRow)
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                CSVWriter(FileWriter(file)).use { writer ->
                    writer.writeNext(arrayOf("libraryId", "library_rev", "title", "description", "resourceRemoteAddress", "resourceLocalAddress", "resourceOffline", "resourceId", "addedBy", "uploadDate", "createdDate", "openWith", "articleDate", "kind", "language", "author", "year", "medium", "filename", "mediaType", "resourceType", "timesRated", "averageRating", "publisher", "linkToLicense", "subject", "level", "tags", "languages", "courseId", "stepId", "downloaded", "private"))
                    data.forEach { row ->
                        writer.writeNext(row)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun libraryWriteCsv() {
            writeCsv("${context.getExternalFilesDir(null)}/ole/library.csv", libraryDataList)
        }

        @JvmStatic
        fun listToString(list: RealmList<String>?): String {
            return list?.joinToString(", ") ?: ""
        }

        @JvmStatic
        fun save(allDocs: JsonArray, mRealm: Realm): List<String> {
            val list: MutableList<String> = ArrayList()
            allDocs.forEach { doc ->
                val document = JsonUtils.getJsonObject("doc", doc.asJsonObject)
                val id = JsonUtils.getString("_id", document)
                if (!id.startsWith("_design")) {
                    list.add(id)
                    insertResources(document, mRealm)
                }
            }
            return list
        }

        @JvmStatic
        fun getMyLibIds(realm: Realm?, userId: String?): JsonArray {
            val myLibraries = userId?.let { realm?.where(RealmMyLibrary::class.java)?.contains("userId", it)?.findAll() }
            return JsonArray().apply { myLibraries?.forEach { lib -> add(lib.id) }
            }
        }
        @JvmStatic
        fun getLevels(libraries: List<RealmMyLibrary>): Set<String> {
            return libraries.flatMap { it.level ?: emptyList() }.toSet()
        }

        @JvmStatic
        fun getArrayList(libraries: List<RealmMyLibrary>, type: String): Set<String?> {
            return libraries.mapNotNull { if (type == "mediums") it.mediaType else it.language }.filterNot { it.isNullOrBlank() }.toSet()
        }

        @JvmStatic
        fun getSubjects(libraries: List<RealmMyLibrary>): Set<String> {
            return libraries.flatMap { it.subject ?: emptyList() }.toSet()
        }
    }
}