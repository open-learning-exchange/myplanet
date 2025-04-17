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
import java.util.Calendar
import java.util.Date
import java.util.UUID
import kotlin.collections.component1
import kotlin.collections.component2

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
        var BATCH_SIZE = 30
//        private const val TAG = "RealmMyLibrary" // Log tag

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
            val idsToDelete = ids.filterNot { it in newIds }

            // Check if we're already in a transaction
            val wasInTransaction = mRealm.isInTransaction

            if (!wasInTransaction) {
                mRealm.beginTransaction()
            }

            try {
                for (id in idsToDelete) {
                    mRealm.where(RealmMyLibrary::class.java).equalTo("resourceId", id).findAll().deleteAllFromRealm()
                }

                // Only commit if we started the transaction
                if (!wasInTransaction) {
                    mRealm.commitTransaction()
                }
            } catch (e: Exception) {
                if (!wasInTransaction && mRealm.isInTransaction) {
                    mRealm.cancelTransaction()
                }
                throw e
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

//        private fun insertResources(doc: JsonObject, mRealm: Realm) {
//            insertMyLibrary("", doc, mRealm)
//        }

        @JvmStatic
        fun createStepResource(mRealm: Realm, res: JsonObject, myCoursesID: String?, stepId: String?) {
            val settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!mRealm.isInTransaction) {
                mRealm.beginTransaction()
            }

            try {
                val resourceId = JsonUtils.getString("_id", res)
                var resource = mRealm.where(RealmMyLibrary::class.java).equalTo("id", resourceId).findFirst()
                if (resource == null) {
                    resource = mRealm.createObject(RealmMyLibrary::class.java, resourceId)
                }

                resource?.apply {
                    if (!stepId.isNullOrBlank()) {
                        this.stepId = stepId
                    }
                    if (!myCoursesID.isNullOrBlank()) {
                        this.courseId = myCoursesID
                    }
                    _rev = JsonUtils.getString("_rev", res)
                    this.resourceId = resourceId
                    title = JsonUtils.getString("title", res)
                    description = JsonUtils.getString("description", res)
                    if (res.has("_attachments")) {
                        val attachments = res["_attachments"].asJsonObject
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
                    filename = JsonUtils.getString("filename", res)
                    averageRating = JsonUtils.getString("averageRating", res)
                    uploadDate = JsonUtils.getString("uploadDate", res)
                    year = JsonUtils.getString("year", res)
                    addedBy = JsonUtils.getString("addedBy", res)
                    publisher = JsonUtils.getString("publisher", res)
                    linkToLicense = JsonUtils.getString("linkToLicense", res)
                    openWith = JsonUtils.getString("openWith", res)
                    articleDate = JsonUtils.getString("articleDate", res)
                    kind = JsonUtils.getString("kind", res)
                    createdDate = JsonUtils.getLong("createdDate", res)
                    language = JsonUtils.getString("language", res)
                    author = JsonUtils.getString("author", res)
                    mediaType = JsonUtils.getString("mediaType", res)
                    resourceType = JsonUtils.getString("resourceType", res)
                    timesRated = JsonUtils.getInt("timesRated", res)
                    medium = JsonUtils.getString("medium", res)
                    setResourceFor(JsonUtils.getJsonArray("resourceFor", res), this)
                    setSubject(JsonUtils.getJsonArray("subject", res), this)
                    setLevel(JsonUtils.getJsonArray("level", res), this)
                    setTag(JsonUtils.getJsonArray("tags", res), this)
                    isPrivate = JsonUtils.getBoolean("private", res)
                    setLanguages(JsonUtils.getJsonArray("languages", res), this)
                }

                mRealm.commitTransaction()
            } catch (e: Exception) {
                if (mRealm.isInTransaction) {
                    mRealm.cancelTransaction()
                }
                e.printStackTrace()
            }
        }

//        @JvmStatic
//        fun createStepResource(mRealm: Realm, res: JsonObject, myCoursesID: String?, stepId: String?) {
//            insertMyLibrary("", stepId, myCoursesID, res, mRealm)
//        }

//        @JvmStatic
//        fun insertMyLibrary(userId: String?, doc: JsonObject, mRealm: Realm) {
//            val jsonArray = JsonArray().apply { add(doc) }
//            batchInsertMyLibrary(listOf(userId), jsonArray, mRealm)
//        }

//        @JvmStatic
//        fun insertMyLibrary(userId: String?, doc: JsonObject, mRealm: Realm) {
//            insertMyLibrary(userId, "", "", doc, mRealm)
//        }

        @JvmStatic
        fun createFromResource(resource: RealmMyLibrary?, mRealm: Realm, userId: String?) {
            if (!mRealm.isInTransaction) {
                mRealm.beginTransaction()
            }
            resource?.setUserId(userId)
            mRealm.commitTransaction()
        }

        @JvmStatic
        fun batchInsertMyLibrary(userIds: List<String?>, docs: JsonArray, mRealm: Realm) {
            val totalDocs = docs.size()
            Log.d("Batch", "Starting batch insertion of $totalDocs resources")
            val startTime = System.currentTimeMillis()

            val runtime = Runtime.getRuntime()
            val initialMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
            Log.d("Memory", "Initial memory usage: ${initialMemory}MB")

            val resourceBatches = docs.asSequence()
                .map { it.asJsonObject }
                .filter { JsonUtils.getString("_id", it).let { id -> !id.startsWith("_design") } }
                .chunked(BATCH_SIZE)

            val settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var batchCount = 0
            var totalProcessed = 0

            for (batch in resourceBatches) {
                batchCount++
                val batchStartTime = System.currentTimeMillis()
                logMemoryUsage("Before batch #$batchCount")

                val memoryBeforeBatch = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                Log.d("Memory", "Before batch #$batchCount - Memory usage: ${memoryBeforeBatch}MB, diff from start: ${memoryBeforeBatch - initialMemory}MB")

                Log.d("Batch", "Processing batch #$batchCount with ${batch.size} resources")

                if (!mRealm.isInTransaction) {
                    mRealm.beginTransaction()
                }

                try {
                    var slowestRecordTime = 0L
                    var slowestRecordId = ""

                    for ((index, doc) in batch.withIndex()) {
                        val recordStartTime = System.currentTimeMillis()
                        val resourceId = JsonUtils.getString("_id", doc)
                        Log.v("Processing", "Processing resource $resourceId (${totalProcessed + index + 1}/$totalDocs)")

                        val userId = if (userIds.size > index) userIds[index] else null

                        var resource = mRealm.where(RealmMyLibrary::class.java).equalTo("id", resourceId).findFirst()
                        if (resource == null) {
                            resource = mRealm.createObject(RealmMyLibrary::class.java, resourceId)
                            Log.v("resource", "Created new resource with ID: $resourceId")
                        } else {
                            Log.v("resource", "Updating existing resource with ID: $resourceId")
                        }

                        resource?.apply {
                            setUserId(userId)
                            _id = resourceId
                            _rev = JsonUtils.getString("_rev", doc)
                            this.resourceId = resourceId
                            title = JsonUtils.getString("title", doc)
                            description = JsonUtils.getString("description", doc)

                            val attachmentStartTime = System.currentTimeMillis()
                            val docSize = doc.toString().length
                            val hasAttachments = doc.has("_attachments")
                            val attachmentCount = if (hasAttachments) doc["_attachments"].asJsonObject.entrySet().size else 0
                            var totalAttachmentSize = 0L
                            if (hasAttachments) {
                                doc["_attachments"].asJsonObject.entrySet().forEach { (_, value) ->
                                    val attachmentObj = value.asJsonObject
                                    totalAttachmentSize += attachmentObj.get("length")?.asLong ?: 0
                                }
                            }

                            if (docSize > 10000 || totalAttachmentSize > 500000) {
                                Log.w("LargeDoc", "Large document found: $resourceId, " +
                                        "doc size: ${docSize/1024}KB, " +
                                        "attachments: $attachmentCount, " +
                                        "attachment size: ${totalAttachmentSize/1024}KB")
                            }
                            if (doc.has("_attachments")) {
                                val attachments = doc["_attachments"].asJsonObject
                                if (this.attachments == null) {
                                    this.attachments = RealmList()
                                }

                                val attachmentCount = attachments.entrySet().size
                                Log.v("resource", "Resource $resourceId has $attachmentCount attachments")

                                attachments.entrySet().forEach { (key, attachmentValue) ->
                                    val attachmentObj = attachmentValue.asJsonObject
                                    val attachmentSize = attachmentObj.get("length")?.asLong ?: 0

                                    // Log large attachments
                                    if (attachmentSize > 1024 * 1024) { // 1MB
                                        Log.w("LargeAttachment", "Large attachment in $resourceId: $key, size: ${attachmentSize/1024/1024}MB")
                                    }

                                    val realmAttachment = mRealm.createObject(RealmAttachment::class.java, UUID.randomUUID().toString())
                                    realmAttachment.apply {
                                        name = key
                                        contentType = attachmentObj.get("content_type")?.asString
                                        length = attachmentSize
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

                            // Populate all other fields
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

                        // Add to CSV data (optional: could be batched as well)
                        addToCsvData(doc)

                        val recordEndTime = System.currentTimeMillis()
                        val recordProcessingTime = recordEndTime - recordStartTime

                        // Log slow records for investigation
                        if (recordProcessingTime > 1000) {
                            Log.w("processing", "Slow record processing: $resourceId took $recordProcessingTime ms")
                        }

                        // Track the slowest record in this batch
                        if (recordProcessingTime > slowestRecordTime) {
                            slowestRecordTime = recordProcessingTime
                            slowestRecordId = resourceId
                        }

                        if (index > 0 && index % 10 == 0) {
                            val commitStartTime = System.currentTimeMillis()
                            mRealm.commitTransaction()
                            val commitEndTime = System.currentTimeMillis()
                            Log.d("transaction", "Intermediate commit took ${commitEndTime - commitStartTime}ms")
                            mRealm.beginTransaction()
                        }
                    }

                    val memoryAfterProcessing = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                    Log.d("Memory", "After processing batch #$batchCount - Memory usage: ${memoryAfterProcessing}MB, diff: ${memoryAfterProcessing - memoryBeforeBatch}MB")

                    // Final commit for this batch
                    val commitStartTime = System.currentTimeMillis()

                    if (mRealm.isInTransaction) {
                        val commitStartTime = System.currentTimeMillis()
                        mRealm.commitTransaction()
                        val commitEndTime = System.currentTimeMillis()
                        Log.d("transaction", "Final commit took ${commitEndTime - commitStartTime}ms")
                    }

                    val commitEndTime = System.currentTimeMillis()
                    Log.d("transaction", "Final batch commit took ${commitEndTime - commitStartTime}ms")

                    // After final commit
                    val memoryAfterCommit = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                    Log.d("Memory", "After committing batch #$batchCount - Memory usage: ${memoryAfterCommit}MB, diff: ${memoryAfterCommit - memoryAfterProcessing}MB")

                    totalProcessed += batch.size

                    val batchEndTime = System.currentTimeMillis()
                    val batchProcessingTime = batchEndTime - batchStartTime
                    Log.d("processing", "Batch #$batchCount completed in ${batchEndTime - batchStartTime}ms")
                    logMemoryUsage("After commit batch #$batchCount")

                    Log.d("processing", "Batch #$batchCount stats: Total time ${batchProcessingTime}ms, " +
                            "Avg time per record ${batchProcessingTime / batch.size}ms, " +
                            "Slowest record $slowestRecordId (${slowestRecordTime}ms)")
                } catch (e: Exception) {
                    Log.e("processing", "Error processing batch #$batchCount", e)
                    if (mRealm.isInTransaction) {
                        mRealm.cancelTransaction()
                    }
                    e.printStackTrace()
                }

                // Run garbage collection to see if memory can be freed
                val memoryBeforeGC = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                System.gc()
                Thread.sleep(100) // Give GC a moment to run
                val memoryAfterGC = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                Log.d("Memory", "After GC for batch #$batchCount - Memory usage: ${memoryAfterGC}MB, freed: ${memoryBeforeGC - memoryAfterGC}MB")

                val endTime = System.currentTimeMillis()
                Log.d("batch", "Completed batch insertion of $totalProcessed resources in ${endTime - startTime}ms")
            }
            val finalMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
            Log.d("Memory", "Final memory usage: ${finalMemory}MB, total change: ${finalMemory - initialMemory}MB")
        }

        private fun logMemoryUsage(tag: String) {
            val rt = Runtime.getRuntime()
            val usedMemory = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
            val freeMemory = rt.freeMemory() / (1024 * 1024)
            val totalMemory = rt.totalMemory() / (1024 * 1024)
            val maxMemory = rt.maxMemory() / (1024 * 1024)

            Log.d("memory", "$tag - Memory: Used=${usedMemory}MB, Free=${freeMemory}MB, " +
                    "Total=${totalMemory}MB, Max=${maxMemory}MB")
        }

//        @JvmStatic
//        fun insertMyLibrary(userId: String?, stepId: String?, courseId: String?, doc: JsonObject, mRealm: Realm) {
//            if (!mRealm.isInTransaction) {
//                mRealm.beginTransaction()
//            }
//            val resourceId = JsonUtils.getString("_id", doc)
//            val settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
//            var resource = mRealm.where(RealmMyLibrary::class.java).equalTo("id", resourceId).findFirst()
//            if (resource == null) {
//                resource = mRealm.createObject(RealmMyLibrary::class.java, resourceId)
//            }
//            resource?.apply {
//                setUserId(userId)
//                _id = resourceId
//                if (!stepId.isNullOrBlank()) {
//                    this.stepId = stepId
//                }
//                if (!courseId.isNullOrBlank()) {
//                    this.courseId = courseId
//                }
//                _rev = JsonUtils.getString("_rev", doc)
//                this.resourceId = resourceId
//                title = JsonUtils.getString("title", doc)
//                description = JsonUtils.getString("description", doc)
//                if (doc.has("_attachments")) {
//                    val attachments = doc["_attachments"].asJsonObject
//                    if (this.attachments == null) {
//                        this.attachments = RealmList()
//                    }
//
//                    attachments.entrySet().forEach { (key, attachmentValue) ->
//                        val attachmentObj = attachmentValue.asJsonObject
//
//                        val realmAttachment = mRealm.createObject(RealmAttachment::class.java, UUID.randomUUID().toString())
//                        realmAttachment.apply {
//                            name = key
//                            contentType = attachmentObj.get("content_type")?.asString
//                            length = attachmentObj.get("length")?.asLong ?: 0
//                            digest = attachmentObj.get("digest")?.asString
//                            isStub = attachmentObj.get("stub")?.asBoolean == true
//                            revpos = attachmentObj.get("revpos")?.asInt ?: 0
//                        }
//
//                        this.attachments?.add(realmAttachment)
//
//                        if (key.indexOf("/") < 0) {
//                            resourceRemoteAddress = "${settings.getString("couchdbURL", "http://")}/resources/$resourceId/$key"
//                            resourceLocalAddress = key
//                            resourceOffline = FileUtils.checkFileExist(resourceRemoteAddress)
//                        }
//                    }
//                }
//                filename = JsonUtils.getString("filename", doc)
//                averageRating = JsonUtils.getString("averageRating", doc)
//                uploadDate = JsonUtils.getString("uploadDate", doc)
//                year = JsonUtils.getString("year", doc)
//                addedBy = JsonUtils.getString("addedBy", doc)
//                publisher = JsonUtils.getString("publisher", doc)
//                linkToLicense = JsonUtils.getString("linkToLicense", doc)
//                openWith = JsonUtils.getString("openWith", doc)
//                articleDate = JsonUtils.getString("articleDate", doc)
//                kind = JsonUtils.getString("kind", doc)
//                createdDate = JsonUtils.getLong("createdDate", doc)
//                language = JsonUtils.getString("language", doc)
//                author = JsonUtils.getString("author", doc)
//                mediaType = JsonUtils.getString("mediaType", doc)
//                resourceType = JsonUtils.getString("resourceType", doc)
//                timesRated = JsonUtils.getInt("timesRated", doc)
//                medium = JsonUtils.getString("medium", doc)
//                setResourceFor(JsonUtils.getJsonArray("resourceFor", doc), this)
//                setSubject(JsonUtils.getJsonArray("subject", doc), this)
//                setLevel(JsonUtils.getJsonArray("level", doc), this)
//                setTag(JsonUtils.getJsonArray("tags", doc), this)
//                isPrivate = JsonUtils.getBoolean("private", doc)
//                setLanguages(JsonUtils.getJsonArray("languages", doc), this)
//            }
//            mRealm.commitTransaction()
//
//            val csvRow = arrayOf(
//                JsonUtils.getString("_id", doc),
//                JsonUtils.getString("_rev", doc),
//                JsonUtils.getString("title", doc),
//                JsonUtils.getString("description", doc),
//                JsonUtils.getString("resourceRemoteAddress", doc),
//                JsonUtils.getString("resourceLocalAddress", doc),
//                JsonUtils.getBoolean("resourceOffline", doc).toString(),
//                JsonUtils.getString("resourceId", doc),
//                JsonUtils.getString("addedBy", doc),
//                JsonUtils.getString("uploadDate", doc),
//                JsonUtils.getLong("createdDate", doc).toString(),
//                JsonUtils.getString("openWith", doc),
//                JsonUtils.getString("articleDate", doc),
//                JsonUtils.getString("kind", doc),
//                JsonUtils.getString("language", doc),
//                JsonUtils.getString("author", doc),
//                JsonUtils.getString("year", doc),
//                JsonUtils.getString("medium", doc),
//                JsonUtils.getString("filename", doc),
//                JsonUtils.getString("mediaType", doc),
//                JsonUtils.getString("resourceType", doc),
//                JsonUtils.getInt("timesRated", doc).toString(),
//                JsonUtils.getString("averageRating", doc),
//                JsonUtils.getString("publisher", doc),
//                JsonUtils.getString("linkToLicense", doc),
//                JsonUtils.getString("subject", doc),
//                JsonUtils.getString("level", doc),
//                JsonUtils.getString("tags", doc),
//                JsonUtils.getString("languages", doc),
//                JsonUtils.getString("courseId", doc),
//                JsonUtils.getString("stepId", doc),
//                JsonUtils.getString("downloaded", doc),
//                JsonUtils.getBoolean("private", doc).toString(),
//            )
//            libraryDataList.add(csvRow)
//        }

        private fun addToCsvData(doc: JsonObject) {
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
            Log.d("save", "save called with ${allDocs.size()} documents")
            val startTime = System.currentTimeMillis()
            val list: MutableList<String> = ArrayList()
            val filteredDocs = JsonArray()

            allDocs.forEach { doc ->
                val document = JsonUtils.getJsonObject("doc", doc.asJsonObject)
                val id = JsonUtils.getString("_id", document)
                if (!id.startsWith("_design")) {
                    list.add(id)
                    filteredDocs.add(document)
                }
            }

            Log.d("save", "Filtered ${filteredDocs.size()} valid documents from ${allDocs.size()} total")
            batchInsertMyLibrary(List(filteredDocs.size()) { null }, filteredDocs, mRealm)

            val endTime = System.currentTimeMillis()
            Log.d("save", "save completed in ${endTime - startTime}ms")
            return list
        }

//        @JvmStatic
//        fun save(allDocs: JsonArray, mRealm: Realm): List<String> {
//            val list: MutableList<String> = ArrayList()
//            allDocs.forEach { doc ->
//                val document = JsonUtils.getJsonObject("doc", doc.asJsonObject)
//                val id = JsonUtils.getString("_id", document)
//                if (!id.startsWith("_design")) {
//                    list.add(id)
//                    insertResources(document, mRealm)
//                }
//            }
//            return list
//        }

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
