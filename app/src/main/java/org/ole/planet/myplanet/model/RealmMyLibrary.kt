package org.ole.planet.myplanet.model

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.opencsv.CSVWriter
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.SyncTimingLogger
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.UUID
import java.util.concurrent.Executors

open class RealmMyLibrary : RealmObject() {
    @PrimaryKey
    var id: String? = null

    @Index
    var _id: String? = null

    var userId: RealmList<String>? = null
        private set

    var resourceRemoteAddress: String? = null
    var resourceLocalAddress: String? = null
    var resourceOffline: Boolean = false

    @Index
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
    @Index
    var title: String? = null
    var averageRating: String? = null
    var filename: String? = null
    @Index
    var mediaType: String? = null
    @Index
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
    @Index
    var courseId: String? = null
    @Index
    var stepId: String? = null
    var isPrivate: Boolean = false
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

    fun setResourceFor(array: JsonArray?) {
        if (array == null) return

        if (this.resourceFor == null) {
            this.resourceFor = RealmList()
        }

        // Prepare a set of existing values to avoid repeated contains checks
        val existingValues = this.resourceFor?.toSet() ?: emptySet()

        for (i in 0 until array.size()) {
            val element = array[i]
            if (element !is JsonNull) {
                val value = element.asString
                if (value !in existingValues) {
                    this.resourceFor?.add(value)
                }
            }
        }
    }

    fun setSubject(array: JsonArray?) {
        if (array == null) return

        if (this.subject == null) {
            this.subject = RealmList()
        }

        val existingValues = this.subject?.toSet() ?: emptySet()

        for (i in 0 until array.size()) {
            val element = array[i]
            if (element !is JsonNull) {
                val value = element.asString
                if (value !in existingValues) {
                    this.subject?.add(value)
                }
            }
        }
    }

    fun setLevel(array: JsonArray?) {
        if (array == null) return

        if (this.level == null) {
            this.level = RealmList()
        }

        val existingValues = this.level?.toSet() ?: emptySet()

        for (i in 0 until array.size()) {
            val element = array[i]
            if (element !is JsonNull) {
                val value = element.asString
                if (value !in existingValues) {
                    this.level?.add(value)
                }
            }
        }
    }

    fun setTag(array: JsonArray?) {
        if (array == null) return

        if (this.tag == null) {
            this.tag = RealmList()
        }

        val existingValues = this.tag?.toSet() ?: emptySet()

        for (i in 0 until array.size()) {
            val element = array[i]
            if (element !is JsonNull) {
                val value = element.asString
                if (value !in existingValues) {
                    this.tag?.add(value)
                }
            }
        }
    }

    fun setLanguages(array: JsonArray?) {
        if (array == null) return

        if (this.languages == null) {
            this.languages = RealmList()
        }

        val existingValues = this.languages?.toSet() ?: emptySet()

        for (i in 0 until array.size()) {
            val element = array[i]
            if (element !is JsonNull) {
                val value = element.asString
                if (value !in existingValues) {
                    this.languages?.add(value)
                }
            }
        }
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
        private val TAG = "RealmMyLibrary"
        val libraryDataList: MutableList<Array<String>> = Collections.synchronizedList(mutableListOf())
        private val csvWriteExecutor = Executors.newSingleThreadExecutor()

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
            val idsToRemove = ids.filterNot { it in newIds }

            if (idsToRemove.isNotEmpty()) {
                try {
                    mRealm.executeTransaction { realm ->
                        for (id in idsToRemove) {
                            realm.where(RealmMyLibrary::class.java)
                                .equalTo("resourceId", id)
                                .findAll()
                                .deleteAllFromRealm()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing deleted resources: ${e.message}", e)
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

        @JvmStatic
        fun save(allDocs: JsonArray, mRealm: Realm): List<String> {
            val list: MutableList<String> = Collections.synchronizedList(ArrayList())
            val batchSize = 50

            SyncTimingLogger.logOperation("library_save_parallel_start")

            try {
                // Group documents into batches
                val batches = mutableListOf<List<JsonObject>>()
                val currentBatch = mutableListOf<JsonObject>()

                allDocs.forEach { doc ->
                    val document = JsonUtils.getJsonObject("doc", doc.asJsonObject)
                    val id = JsonUtils.getString("_id", document)
                    if (!id.startsWith("_design")) {
                        list.add(id)
                        currentBatch.add(document)

                        if (currentBatch.size >= batchSize) {
                            batches.add(currentBatch.toList())
                            currentBatch.clear()
                        }
                    }
                }

                // Add any remaining documents
                if (currentBatch.isNotEmpty()) {
                    batches.add(currentBatch.toList())
                }

                SyncTimingLogger.logOperation("library_batches_created_${batches.size}")

                // Process batches in parallel using coroutines
                runBlocking {
                    val results = batches.mapIndexed { index, batch ->
                        async(Dispatchers.IO) {
                            val batchRealm = Realm.getDefaultInstance()
                            try {
                                SyncTimingLogger.logOperation("library_batch_${index}_start")
                                batchInsertResources(batch, batchRealm)
                                SyncTimingLogger.logOperation("library_batch_${index}_complete")
                            } finally {
                                batchRealm.close()
                            }
                        }
                    }

                    // Wait for all batches to complete
                    results.awaitAll()
                }

                SyncTimingLogger.logOperation("library_save_parallel_complete")
            } catch (e: Exception) {
                Log.e(TAG, "Error in parallel save method: ${e.message}", e)
            }

            return list
        }

        private fun batchInsertResources(documents: List<JsonObject>, mRealm: Realm) {
            try {
                SyncTimingLogger.logOperation("batch_insert_resources_start_${documents.size}")

                if (!mRealm.isInTransaction) {
                    mRealm.beginTransaction()
                }

                for (doc in documents) {
                    try {
                        // Fix: Pass correct parameters to match method signature
                        insertResourceInTransaction(userId = "", doc = doc, mRealm = mRealm)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing document: ${e.message}")
                        // Continue with other documents
                    }
                }

                mRealm.commitTransaction()

                SyncTimingLogger.logOperation("batch_insert_resources_complete")
            } catch (e: Exception) {
                if (mRealm.isInTransaction) {
                    mRealm.cancelTransaction()
                }
                Log.e(TAG, "Error in batch insert: ${e.message}", e)
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
            insertMyLibrary(userId, "", "", doc, mRealm)
        }

        @JvmStatic
        fun createFromResource(resource: RealmMyLibrary?, mRealm: Realm, userId: String?) {
            val wasInTransaction = mRealm.isInTransaction

            try {
                if (!wasInTransaction) {
                    mRealm.beginTransaction()
                }

                resource?.setUserId(userId)

                if (!wasInTransaction) {
                    mRealm.commitTransaction()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating from resource: ${e.message}", e)
                if (!wasInTransaction && mRealm.isInTransaction) {
                    mRealm.cancelTransaction()
                }
            }
        }

        @JvmStatic
        fun insertMyLibrary(userId: String?, stepId: String?, courseId: String?, doc: JsonObject, mRealm: Realm) {
            val wasInTransaction = mRealm.isInTransaction

            try {
                if (!wasInTransaction) {
                    mRealm.beginTransaction()
                }

                insertResourceInTransaction(userId, stepId, courseId, doc, mRealm)

                if (!wasInTransaction) {
                    mRealm.commitTransaction()
                }

                // Add CSV row outside of transaction
                addCsvRow(doc)
            } catch (e: Exception) {
                Log.e(TAG, "Error inserting library: ${e.message}", e)
                if (!wasInTransaction && mRealm.isInTransaction) {
                    mRealm.cancelTransaction()
                }
            }
        }

        private fun insertResourceInTransaction(userId: String?, stepId: String? = "", courseId: String? = "", doc: JsonObject, mRealm: Realm) {
            val resourceId = JsonUtils.getString("_id", doc)
            val settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var resource = mRealm.where(RealmMyLibrary::class.java).equalTo("id", resourceId).findFirst()

            if (resource == null) {
                resource = mRealm.createObject(RealmMyLibrary::class.java, resourceId)
            }

            resource?.apply {
                if (!userId.isNullOrBlank()) {
                    setUserId(userId)
                }

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

                // Process attachments - standard approach without lazy loading
                if (doc.has("_attachments")) {
                    val attachments = doc["_attachments"].asJsonObject

                    if (this.attachments == null) {
                        this.attachments = RealmList()
                    }

                    attachments.entrySet().forEach { (key, attachmentValue) ->
                        try {
                            val attachmentObj = attachmentValue.asJsonObject

                            // Create attachment
                            val realmAttachment = mRealm.createObject(RealmAttachment::class.java, UUID.randomUUID().toString())
                            realmAttachment.apply {
                                name = key
                                contentType = attachmentObj.get("content_type")?.asString
                                length = attachmentObj.get("length")?.asLong ?: 0
                                digest = attachmentObj.get("digest")?.asString
                                isStub = attachmentObj.get("stub")?.asBoolean == true
                                revpos = attachmentObj.get("revpos")?.asInt ?: 0
                            }

                            this.attachments?.add(realmAttachment)

                            // Set resource properties based on attachment
                            if (key.indexOf("/") < 0) {
                                resourceRemoteAddress = "${settings.getString("couchdbURL", "http://")}/resources/$resourceId/$key"
                                resourceLocalAddress = key
                                resourceOffline = FileUtils.checkFileExist(resourceRemoteAddress)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing attachment $key: ${e.message}")
                        }
                    }
                }

                // Set basic properties
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
                isPrivate = JsonUtils.getBoolean("private", doc)

                // Set list properties efficiently
                if (doc.has("resourceFor")) {
                    setResourceFor(JsonUtils.getJsonArray("resourceFor", doc))
                }

                if (doc.has("subject")) {
                    setSubject(JsonUtils.getJsonArray("subject", doc))
                }

                if (doc.has("level")) {
                    setLevel(JsonUtils.getJsonArray("level", doc))
                }

                if (doc.has("tags")) {
                    setTag(JsonUtils.getJsonArray("tags", doc))
                }

                if (doc.has("languages")) {
                    setLanguages(JsonUtils.getJsonArray("languages", doc))
                }
            }
        }

        private fun addCsvRow(doc: JsonObject) {
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

            synchronized(libraryDataList) {
                libraryDataList.add(csvRow)
            }
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                CSVWriter(FileWriter(file)).use { writer ->
                    writer.writeNext(arrayOf("libraryId", "library_rev", "title", "description", "resourceRemoteAddress", "resourceLocalAddress", "resourceOffline", "resourceId", "addedBy", "uploadDate", "createdDate", "openWith", "articleDate", "kind", "language", "author", "year", "medium", "filename", "mediaType", "resourceType", "timesRated", "averageRating", "publisher", "linkToLicense", "subject", "level", "tags", "languages", "courseId", "stepId", "downloaded", "private"))

                    synchronized(data) {
                        data.forEach { row ->
                            writer.writeNext(row)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error writing CSV: ${e.message}", e)
            }
        }

        fun libraryWriteCsv() {
            // Run CSV writing in a background thread with optimized buffer
            csvWriteExecutor.execute {
                try {
                    SyncTimingLogger.logOperation("library_csv_write_start")

                    val dataToWrite: List<Array<String>>
                    synchronized(libraryDataList) {
                        dataToWrite = ArrayList(libraryDataList)
                    }

                    // Use buffered writer with larger buffer size
                    val file = File("${context.getExternalFilesDir(null)}/ole/library.csv")
                    file.parentFile?.mkdirs()

                    BufferedWriter(FileWriter(file), 32768).use { writer ->
                        // Write header
                        writer.write("libraryId,library_rev,title,description,resourceRemoteAddress,resourceLocalAddress,resourceOffline,resourceId,addedBy,uploadDate,createdDate,openWith,articleDate,kind,language,author,year,medium,filename,mediaType,resourceType,timesRated,averageRating,publisher,linkToLicense,subject,level,tags,languages,courseId,stepId,downloaded,private\n")

                        // Write data with manual CSV formatting for speed
                        for (row in dataToWrite) {
                            val line = StringBuilder()
                            for (i in row.indices) {
                                if (i > 0) line.append(',')

                                val value = row[i]
                                // Escape CSV field if needed
                                if (value.contains(',') || value.contains('"') || value.contains('\n')) {
                                    line.append('"').append(value.replace("\"", "\"\"")).append('"')
                                } else {
                                    line.append(value)
                                }
                            }
                            line.append('\n')
                            writer.write(line.toString())
                        }
                    }

                    SyncTimingLogger.logOperation("library_csv_write_complete")
                } catch (e: Exception) {
                    Log.e(TAG, "Error in optimized CSV writing: ${e.message}", e)
                }
            }
        }

        @JvmStatic
        fun listToString(list: RealmList<String>?): String {
            return list?.joinToString(", ") ?: ""
        }

        @JvmStatic
        fun getMyLibIds(realm: Realm?, userId: String?): JsonArray {
            val myLibraries = userId?.let { realm?.where(RealmMyLibrary::class.java)?.contains("userId", it)?.findAll() }
            return JsonArray().apply {
                myLibraries?.forEach { lib -> add(lib.id) }
            }
        }

        @JvmStatic
        fun getLevels(libraries: List<RealmMyLibrary>): Set<String> {
            return libraries.flatMap { it.level ?: emptyList() }.toSet()
        }

        @JvmStatic
        fun getArrayList(libraries: List<RealmMyLibrary>, type: String): Set<String?> {
            return libraries.mapNotNull { if (type == "mediums") it.mediaType else it.language }.filterNot { it.isBlank() }.toSet()
        }

        @JvmStatic
        fun getSubjects(libraries: List<RealmMyLibrary>): Set<String> {
            return libraries.flatMap { it.subject ?: emptyList() }.toSet()
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