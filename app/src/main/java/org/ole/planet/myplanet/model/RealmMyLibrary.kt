package org.ole.planet.myplanet.model

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.opencsv.CSVWriter
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

class RealmMyLibrary : RealmObject {
    @PrimaryKey
    var id: String = ""
    var _id: String = ""
    private var _userId: RealmList<String> = realmListOf()
    var resourceRemoteAddress: String = ""
    var resourceLocalAddress: String = ""
    var resourceOffline: Boolean = false
    var resourceId: String = ""
    var _rev: String = ""
    var downloadedRev: String = ""
    var needsOptimization: Boolean = false
    var publisher: String = ""
    var linkToLicense: String = ""
    var addedBy: String = ""
    var uploadDate: String = ""
    var createdDate: Long = 0
    var openWith: String = ""
    var articleDate: String = ""
    var kind: String = ""
    var language: String = ""
    var author: String = ""
    var year: String = ""
    var medium: String = ""
    var title: String = ""
    var averageRating: String = ""
    var filename: String = ""
    var mediaType: String = ""
    var resourceType: String = ""
    var description: String = ""
    var translationAudioPath: String = ""
    var sum: Int = 0
    var timesRated: Int = 0
    var resourceFor: RealmList<String> = realmListOf()
    var subject: RealmList<String> = realmListOf()
    var level: RealmList<String> = realmListOf()
    var tag: RealmList<String> = realmListOf()
    var languages: RealmList<String> = realmListOf()
    var courseId: String = ""
    var stepId: String = ""
    var isPrivate: Boolean = false

    var userId: RealmList<String>
        get() = _userId
        private set(value) {
            _userId = value
        }

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

    private fun RealmList<String>.toJsonArray(): JsonArray {
        return JsonArray().apply {
            forEach { add(it) }
        }
    }

    fun setUserId(newUserId: String?) {
        if (newUserId.isNullOrBlank()) return

        if (!_userId.contains(newUserId)) {
            _userId.add(newUserId)
        }
    }

    fun isResourceOffline(): Boolean {
        return resourceOffline && _rev == downloadedRev
    }

    private fun JsonArray?.setListIfNotNull(targetList: RealmList<String>, setter: (String) -> Unit) {
        this?.forEach { jsonElement ->
            val value = jsonElement.takeIf { it !is JsonNull }?.asString ?: return@forEach
            if (value !in targetList) {
                setter(value)
            }
        }
    }

    fun setResourceFor(array: JsonArray) {
        array.setListIfNotNull(resourceFor) { resourceFor.add(it) }
    }

    fun setSubject(array: JsonArray) {
        array.setListIfNotNull(subject) { subject.add(it) }
    }

    fun setLevel(array: JsonArray) {
        array.setListIfNotNull(level) { level.add(it) }
    }

    fun setTag(array: JsonArray) {
        array.setListIfNotNull(tag) { tag.add(it) }
    }

    fun setLanguages(array: JsonArray) {
        array.setListIfNotNull(languages) { languages.add(it) }
    }

    fun setUserId(userId: RealmList<String>) {
        this._userId = userId
    }

    val subjectsAsString: String
        get() = subject.joinToString(", ")

    override fun toString(): String {
        return title
    }

    fun removeUserId(id: String?) {
        _userId.remove(id)
    }

    fun needToUpdate(): Boolean {
        return !resourceOffline || (resourceLocalAddress.isNotBlank() && _rev != downloadedRev)
    }

    companion object {
        val libraryDataList: MutableList<Array<String>> = mutableListOf()

        fun getMyLibraryByUserId(realm: Realm, settings: SharedPreferences?): List<RealmMyLibrary> {
            val libs = realm.query<RealmMyLibrary>().find()
            return getMyLibraryByUserId(settings?.getString("userId", "--"), libs)
        }

        fun getMyLibraryByUserId(userId: String, libs: List<RealmMyLibrary>, realm: Realm): List<RealmMyLibrary> {
            val ids = RealmMyTeam.getResourceIdsByUser(userId, realm)
            return libs.filter { it.userId.contains(userId) || it.resourceId in ids }
        }

        fun getMyLibraryByUserId(userId: String?, libs: List<RealmMyLibrary>): List<RealmMyLibrary> {
            return libs.filter { it.userId.contains(userId) }
        }

        fun getOurLibrary(userId: String?, libs: List<RealmMyLibrary>): List<RealmMyLibrary> {
            return libs.filter { !it.userId.contains(userId) }
        }

        private fun getIds(realm: Realm): Array<String> {
            return realm.query<RealmMyLibrary>().find().map { it.resourceId }.toTypedArray()
        }

        suspend fun removeDeletedResource(newIds: List<String>, realm: Realm) {
            val ids = getIds(realm)
            ids.filterNot { it in newIds }.forEach { id ->
                realm.write {
                    query<RealmMyLibrary>("resourceId == $0", id).find().forEach { delete(it) }
                }
            }
        }

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

        private suspend fun insertResources(doc: JsonObject, realm: Realm) {
            insertMyLibrary("", doc, realm)
        }

        suspend fun createStepResource(realm: Realm, res: JsonObject, myCoursesID: String?, stepId: String?) {
            insertMyLibrary("", stepId, myCoursesID, res, realm)
        }

        fun insertMyLibrary(userId: String?, doc: JsonObject, realm: Realm) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch{
                insertMyLibrary(userId, "", "", doc, realm)
            }
        }

        suspend fun createFromResource(resource: RealmMyLibrary?, realm: Realm, userId: String?) {
            realm.write {
                resource?.setUserId(userId)
            }
        }

        suspend fun insertMyLibrary(userId: String?, stepId: String?, courseId: String?, doc: JsonObject, realm: Realm) {
            val resourceId = JsonUtils.getString("_id", doc)
            val settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            realm.write {
                var resource = query<RealmMyLibrary>("id == $0", resourceId).first().find()
                if (resource == null) {
                    resource = RealmMyLibrary().apply { id = resourceId }
                    copyToRealm(resource)
                }

                resource.apply {
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
                    setResourceFor(JsonUtils.getJsonArray("resourceFor", doc))
                    setSubject(JsonUtils.getJsonArray("subject", doc))
                    setLevel(JsonUtils.getJsonArray("level", doc))
                    setTag(JsonUtils.getJsonArray("tags", doc))
                    isPrivate = JsonUtils.getBoolean("private", doc)
                    setLanguages(JsonUtils.getJsonArray("languages", doc))
                }
            }

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
                    writer.writeNext(arrayOf("libraryId", "library_rev", "title", "description",
                        "resourceRemoteAddress", "resourceLocalAddress", "resourceOffline",
                        "resourceId", "addedBy", "uploadDate", "createdDate", "openWith",
                        "articleDate", "kind", "language", "author", "year", "medium", "filename",
                        "mediaType", "resourceType", "timesRated", "averageRating", "publisher",
                        "linkToLicense", "subject", "level", "tags", "languages", "courseId",
                        "stepId", "downloaded", "private"
                    ))
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

        fun listToString(list: RealmList<String>): String {
            return list.joinToString(", ")
        }

        suspend fun save(allDocs: JsonArray, realm: Realm): List<String> {
            val list = mutableListOf<String>()
            allDocs.forEach { doc ->
                val document = JsonUtils.getJsonObject("doc", doc.asJsonObject)
                val id = JsonUtils.getString("_id", document)
                if (!id.startsWith("_design")) {
                    list.add(id)
                    insertResources(document, realm)
                }
            }
            return list
        }

        fun getMyLibIds(realm: Realm?, userId: String?): JsonArray {
            return JsonArray().apply {
                userId?.let {
                    realm?.query<RealmMyLibrary>("userId CONTAINS $0", it)?.find()?.forEach { lib ->
                        add(lib.id)
                    }
                }
            }
        }

        fun getLevels(libraries: List<RealmMyLibrary>): Set<String> {
            return libraries.flatMap { it.level }.toSet()
        }

        fun getArrayList(libraries: List<RealmMyLibrary>, type: String): Set<String> {
            return libraries.map {
                when (type) {
                    "mediums" -> it.mediaType
                    else -> it.language
                }
            }.filterNot { it.isBlank() }.toSet()
        }

        fun getSubjects(libraries: List<RealmMyLibrary>): Set<String> {
            return libraries.flatMap { it.subject }.toSet()
        }
    }
}