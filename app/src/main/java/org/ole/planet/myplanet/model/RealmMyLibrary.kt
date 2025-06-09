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
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID


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


        // Optimized bulk insertion method for SyncManager
        @JvmStatic
        fun bulkInsertResources(docs: List<JsonObject>, mRealm: Realm, userId: String? = null): List<String> {
            val TAG = "RealmMyLibrary"
            val startTime = System.currentTimeMillis()
            val processedIds = mutableListOf<String>()
            val csvRows = mutableListOf<Array<String>>()
            val errors = mutableListOf<String>()


            // Log bulk operation start
            Log.d(TAG, "Starting bulk insertion of ${docs.size} documents for user: $userId")


            var newInsertions = 0
            var updates = 0
            var skipped = 0


            docs.forEachIndexed { index, doc ->
                try {
                    val resourceId = JsonUtils.getString("_id", doc)


                    if (!resourceId.startsWith("_design")) {
                        var resource = mRealm.where(RealmMyLibrary::class.java).equalTo("id", resourceId).findFirst()
                        val isNewResource = resource == null


                        if (resource == null) {
                            resource = mRealm.createObject(RealmMyLibrary::class.java, resourceId)
                            newInsertions++
                            Log.v(TAG, "Created new resource: $resourceId")
                        } else {
                            updates++
                            Log.v(TAG, "Updated existing resource: $resourceId")
                        }


                        // Fast population using optimized method
                        populateResourceFromJson(resource, doc, userId)
                        processedIds.add(resourceId)


                        // Build CSV row efficiently
                        csvRows.add(createCsvRow(doc))


                        // Log progress for large batches
                        if ((index + 1) % 100 == 0) {
                            Log.d(TAG, "Processed ${index + 1}/${docs.size} documents")
                        }


                    } else {
                        skipped++
                        Log.v(TAG, "Skipped design document: $resourceId")
                    }
                } catch (e: Exception) {
                    val resourceId = JsonUtils.getString("_id", doc)
                    val errorMsg = "Error processing resource $resourceId: ${e.message}"
                    errors.add(errorMsg)
                    Log.e(TAG, errorMsg, e)
                }
            }


            // Add CSV rows in batch
            val csvStartTime = System.currentTimeMillis()
            libraryDataList.addAll(csvRows)
            val csvEndTime = System.currentTimeMillis()


            val totalTime = System.currentTimeMillis() - startTime


            // Comprehensive logging summary
            Log.i(TAG, """
       Bulk insertion completed:
       - Total documents processed: ${docs.size}
       - New insertions: $newInsertions
       - Updates: $updates 
       - Skipped: $skipped
       - Errors: ${errors.size}
       - Successfully processed: ${processedIds.size}
       - CSV rows added: ${csvRows.size}
       - Total time: ${totalTime}ms
       - CSV processing time: ${csvEndTime - csvStartTime}ms
       - Average time per document: ${if (docs.isNotEmpty()) totalTime / docs.size else 0}ms
       - User ID: $userId
   """.trimIndent())


            // Log errors if any
            if (errors.isNotEmpty()) {
                Log.w(TAG, "Bulk insertion encountered ${errors.size} errors:")
                errors.forEach { error ->
                    Log.w(TAG, "  - $error")
                }
            }


            // Log memory usage (optional)
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            Log.d(TAG, "Memory usage after bulk insertion: ${usedMemory / 1024 / 1024}MB")


            logPerformanceMetrics("Bulk Resource Insertion", startTime, docs.size)
            return processedIds
        }


        // Optimized resource population method
        private fun populateResourceFromJson(resource: RealmMyLibrary, doc: JsonObject, userId: String?) {
            val TAG = "RealmMyLibrary"


            try {
                resource.apply {
                    setUserId(userId)
                    _id = JsonUtils.getString("_id", doc)
                    _rev = JsonUtils.getString("_rev", doc)
                    resourceId = _id
                    title = JsonUtils.getString("title", doc)
                    description = JsonUtils.getString("description", doc)


                    // Handle attachments efficiently
                    if (doc.has("_attachments")) {
                        handleAttachmentsOptimized(this, doc)
                    }


                    // Set primitive fields in batch
                    setBulkPrimitiveFields(this, doc)


                    // Set array fields efficiently
                    setBulkArrayFields(this, doc)
                }


                Log.v(TAG, "Successfully populated resource: ${resource._id} - ${resource.title}")


            } catch (e: Exception) {
                Log.e(TAG, "Error populating resource from JSON: ${JsonUtils.getString("_id", doc)}", e)
                throw e // Re-throw to be caught by caller
            }
        }


        // Optimized attachment handling
        private fun handleAttachmentsOptimized(resource: RealmMyLibrary, doc: JsonObject) {
            val TAG = "RealmMyLibrary"


            try {
                val attachments = doc["_attachments"].asJsonObject
                val settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                var attachmentCount = 0


                if (resource.attachments == null) {
                    resource.attachments = RealmList()
                } else {
                    resource.attachments?.clear()
                }


                attachments.entrySet().forEach { (key, attachmentValue) ->
                    try {
                        val attachmentObj = attachmentValue.asJsonObject
                        val realm = resource.realm ?: return@forEach


                        val realmAttachment = realm.createObject(RealmAttachment::class.java, UUID.randomUUID().toString())
                        realmAttachment.apply {
                            name = key
                            contentType = attachmentObj.get("content_type")?.asString
                            length = attachmentObj.get("length")?.asLong ?: 0
                            digest = attachmentObj.get("digest")?.asString
                            isStub = attachmentObj.get("stub")?.asBoolean == true
                            revpos = attachmentObj.get("revpos")?.asInt ?: 0
                        }


                        resource.attachments?.add(realmAttachment)
                        attachmentCount++


                        if (key.indexOf("/") < 0) {
                            resource.resourceRemoteAddress = "${settings.getString("couchdbURL", "http://")}/resources/${resource.resourceId}/$key"
                            resource.resourceLocalAddress = key
                            resource.resourceOffline = FileUtils.checkFileExist(resource.resourceRemoteAddress)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing attachment $key for resource ${resource._id}", e)
                    }
                }


                Log.v(TAG, "Processed $attachmentCount attachments for resource: ${resource._id}")


            } catch (e: Exception) {
                Log.e(TAG, "Error handling attachments for resource: ${resource._id}", e)
                throw e
            }
        }


        // Bulk set primitive fields to reduce method calls
        private fun setBulkPrimitiveFields(resource: RealmMyLibrary, doc: JsonObject) {
            resource.apply {
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
            }
        }


        // Bulk set array fields efficiently
        private fun setBulkArrayFields(resource: RealmMyLibrary, doc: JsonObject) {
            resource.setResourceFor(JsonUtils.getJsonArray("resourceFor", doc), resource)
            resource.setSubject(JsonUtils.getJsonArray("subject", doc), resource)
            resource.setLevel(JsonUtils.getJsonArray("level", doc), resource)
            resource.setTag(JsonUtils.getJsonArray("tags", doc), resource)
            resource.setLanguages(JsonUtils.getJsonArray("languages", doc), resource)
        }


        // Optimized CSV row creation
        private fun createCsvRow(doc: JsonObject): Array<String> {
            return arrayOf(
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
        }


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
                    realm.where(RealmMyLibrary::class.java).equalTo("resourceId", id).findAll()
                        .deleteAllFromRealm()
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
            insertMyLibrary(userId, "", "", doc, mRealm)
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
            if (doc.entrySet().isEmpty()) return
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
                    if (this.attachments == null) {
                        this.attachments = RealmList()
                    }


                    attachments.entrySet().forEach { (key, attachmentValue) ->
                        val attachmentObj = attachmentValue.asJsonObject


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


            val csvRow = createCsvRow(doc)
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
            val TAG = "RealmMyLibrary"
            val startTime = System.currentTimeMillis()
            val filePath = "${context.getExternalFilesDir(null)}/ole/library.csv"


            Log.d(TAG, "Starting CSV write operation to: $filePath")
            Log.d(TAG, "Writing ${libraryDataList.size} rows to CSV")


            try {
                writeCsv(filePath, libraryDataList)
                val totalTime = System.currentTimeMillis() - startTime
                Log.i(TAG, "CSV write completed successfully in ${totalTime}ms")


                // Log file size
                val file = File(filePath)
                if (file.exists()) {
                    Log.d(TAG, "CSV file size: ${file.length() / 1024}KB")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing CSV file", e)
            }
        }


        fun logPerformanceMetrics(operationName: String, startTime: Long, itemCount: Int) {
            val TAG = "RealmMyLibrary_Performance"
            val totalTime = System.currentTimeMillis() - startTime
            val avgTime = if (itemCount > 0) totalTime / itemCount else 0


            Log.i(TAG, """
       Performance Metrics - $operationName:
       - Total time: ${totalTime}ms
       - Items processed: $itemCount
       - Average time per item: ${avgTime}ms
       - Items per second: ${if (totalTime > 0) (itemCount * 1000) / totalTime else 0}
       - Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}
   """.trimIndent())
        }


        @JvmStatic
        fun listToString(list: RealmList<String>?): String {
            return list?.joinToString(", ") ?: ""
        }


        @JvmStatic
        fun save(allDocs: JsonArray, mRealm: Realm): List<String> {
            val list: MutableList<String> = ArrayList()
            val validDocs = mutableListOf<JsonObject>()


            // Pre-filter documents
            allDocs.forEach { doc ->
                val document = JsonUtils.getJsonObject("doc", doc.asJsonObject)
                val id = JsonUtils.getString("_id", document)
                if (!id.startsWith("_design")) {
                    list.add(id)
                    validDocs.add(document)
                }
            }


            // Use bulk insertion for better performance
            if (validDocs.isNotEmpty()) {
                bulkInsertResources(validDocs, mRealm)
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

