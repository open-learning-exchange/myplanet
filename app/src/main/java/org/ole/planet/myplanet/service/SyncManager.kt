package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.text.TextUtils
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import io.realm.Realm
import kotlinx.coroutines.*
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.datamanager.ApiClient.client
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.ManagerSync
import org.ole.planet.myplanet.model.RealmMeetup.Companion.insert
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.insertMyCourses
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.saveConcatenatedLinksToPrefs
//import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.insertMyLibrary
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.removeDeletedResource
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.save
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.insertMyTeams
import org.ole.planet.myplanet.model.RealmResourceActivity.Companion.onSynced
import org.ole.planet.myplanet.model.Rows
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.Constants.ShelfData
import org.ole.planet.myplanet.utilities.JsonUtils.getJsonArray
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.NotificationUtil.cancel
import org.ole.planet.myplanet.utilities.NotificationUtil.create
import org.ole.planet.myplanet.utilities.Utilities
import java.io.IOException
import java.util.Date
import kotlin.system.measureTimeMillis
import androidx.core.content.edit
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.BATCH_SIZE
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.batchInsertMyLibrary
import org.ole.planet.myplanet.utilities.JsonUtils.getJsonObject

class SyncManager private constructor(private val context: Context) {
    private var td: Thread? = null
    private val settings: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    lateinit var mRealm: Realm
    private var isSyncing = false
    private val stringArray = arrayOfNulls<String>(4)
    private var shelfDoc: Rows? = null
    private var listener: SyncListener? = null
    private val dbService: DatabaseService = DatabaseService(context)
    private var backgroundSync: Job? = null
    val _syncState = MutableLiveData<Boolean>()
    private val resourceBuffer = ArrayList<JsonObject>()
    private val userIdBuffer = ArrayList<String?>()
    private val RESOURCE_BUFFER_SIZE = 100

    fun start(listener: SyncListener?, type: String) {
        this.listener = listener
        if (!isSyncing) {
            settings.edit { remove("concatenated_links") }
            listener?.onSyncStarted()
            authenticateAndSync(type)
        }
    }

    private fun destroy() {
        cancelBackgroundSync()
        cancel(context, 111)
        isSyncing = false
        ourInstance = null
        settings.edit { putLong("LastSync", Date().time) }
        listener?.onSyncComplete()
        try {
            if (::mRealm.isInitialized && !mRealm.isClosed) {
                mRealm.close()
                td?.interrupt()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun authenticateAndSync(type: String) {
        td = Thread {
            if (TransactionSyncManager.authenticate()) {
                startSync(type)
            } else {
                handleException(context.getString(R.string.invalid_configuration))
                cleanupMainSync()
            }
        }
        td?.start()
    }

    private fun startSync(type: String) {
        val isFastSync = settings.getBoolean("fastSync", true)
        if (!isFastSync || type == "upload") {
            startFullSync()
        } else {
            startFastSync()
        }
    }

    private fun startFullSync() {
        try {
            initializeSync()

            runBlocking {
                val syncJobs = listOf(
                    async { TransactionSyncManager.syncDb(mRealm, "tablet_users") },
                    async { myLibraryTransactionSync() },
                    async { TransactionSyncManager.syncDb(mRealm, "courses") },
                    async { TransactionSyncManager.syncDb(mRealm, "exams") },
                    async { TransactionSyncManager.syncDb(mRealm, "ratings") },
                    async { TransactionSyncManager.syncDb(mRealm, "courses_progress") },
                    async { TransactionSyncManager.syncDb(mRealm, "achievements") },
                    async { TransactionSyncManager.syncDb(mRealm, "tags") },
                    async { TransactionSyncManager.syncDb(mRealm, "submissions") },
                    async { TransactionSyncManager.syncDb(mRealm, "news") },
                    async { TransactionSyncManager.syncDb(mRealm, "feedback") },
                    async { TransactionSyncManager.syncDb(mRealm, "teams") },
                    async { TransactionSyncManager.syncDb(mRealm, "tasks") },
                    async { TransactionSyncManager.syncDb(mRealm, "login_activities") },
                    async { TransactionSyncManager.syncDb(mRealm, "meetups") },
                    async { TransactionSyncManager.syncDb(mRealm, "health") },
                    async { TransactionSyncManager.syncDb(mRealm, "certifications") },
                    async { TransactionSyncManager.syncDb(mRealm, "team_activities") },
                    async { TransactionSyncManager.syncDb(mRealm, "chat_history") }
                )

                syncJobs.awaitAll()
            }

            ManagerSync.instance?.syncAdmin()
            resourceTransactionSync()
            onSynced(mRealm, settings)
            mRealm.close()
        } catch (err: Exception) {
            err.printStackTrace()
            handleException(err.message)
        } finally {
            destroy()
        }
    }

    private fun startFastSync() {
        try {
            initializeSync()
            syncFirstBatch()

            settings.edit { putLong("LastSync", Date().time) }
            listener?.onSyncComplete()
            destroy()
            startBackgroundSync()
        } catch (err: Exception) {
            err.printStackTrace()
            handleException(err.message)
            destroy()
        }
    }

    private fun cleanupMainSync() {
        cancel(context, 111)
        isSyncing = false
        if (::mRealm.isInitialized && !mRealm.isClosed) {
            mRealm.close()
            td?.interrupt()
        }
    }

    private fun cleanupBackgroundSync() {
        cancelBackgroundSync()
        ourInstance = null
    }

    private fun initializeSync() {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        if (wifiInfo.supplicantState == SupplicantState.COMPLETED) {
            settings.edit { putString("LastWifiSSID", wifiInfo.ssid) }
        }
        isSyncing = true
        create(context, R.mipmap.ic_launcher, "Syncing data", "Please wait...")
        mRealm = dbService.realmInstance
    }

    private fun syncFirstBatch() {
        TransactionSyncManager.syncDb(mRealm, "tablet_users")
        ManagerSync.instance?.syncAdmin()
    }

    private fun startBackgroundSync() {
        _syncState.postValue(true)
        backgroundSync = MainApplication.applicationScope.launch(Dispatchers.IO) {
            try {
                Log.d("SYNC", "Starting parallel background sync...")

                val remainingDatabases = listOf(
                    "teams", "news", "team_activities", "courses", "courses_progress", "exams", "tasks",
                    "chat_history", "ratings", "achievements", "tags", "submissions", "feedback",
                    "meetups", "health", "certifications", "login_activities"
                )

                // Measure the total sync time
                val totalTime = measureTimeMillis {
                    // Launch sync operations in parallel
                    val syncJobs = remainingDatabases.map { database ->
                        async(Dispatchers.IO) { // Ensure each coroutine runs on IO thread
                            try {
                                // Create a new Realm instance for this coroutine
                                val realmInstance = Realm.getDefaultInstance()
                                val timeTaken = measureTimeMillis {
                                    TransactionSyncManager.syncDb(realmInstance, database)
                                }
                                Log.d("SYNC", "Sync for $database completed in $timeTaken ms")
                                realmInstance.close() // Close the Realm instance to avoid leaks
                            } catch (e: Exception) {
                                Log.e("SYNC", "Error syncing $database: ${e.message}", e)
                            }
                        }
                    }

                    // Wait for all sync jobs to complete
                    syncJobs.awaitAll()
                }

                Log.d("SYNC", "All database syncs completed in $totalTime ms")

                // Run additional sync tasks in parallel
                val extraSyncJobs = listOf(
                    async(Dispatchers.IO) {
                        try {
                            val realmInstance = Realm.getDefaultInstance()
                            val timeTaken = measureTimeMillis {
                                myLibraryTransactionSync(realmInstance)
                            }
                            Log.d("SYNC", "Library sync completed in $timeTaken ms")
                            realmInstance.close()
                        } catch (e: Exception) {
                            Log.e("SYNC", "Error syncing library: ${e.message}", e)
                        }
                    },
                    async(Dispatchers.IO) {
                        try {
                            val realmInstance = Realm.getDefaultInstance()
                            val timeTaken = measureTimeMillis {
                                resourceTransactionSync(realmInstance)
                            }
                            Log.d("SYNC", "Resource sync completed in $timeTaken ms")
                            realmInstance.close()
                        } catch (e: Exception) {
                            Log.e("SYNC", "Error syncing resources: ${e.message}", e)
                        }
                    }
                )

                extraSyncJobs.awaitAll()

                // Final sync completion
                val finalRealm = Realm.getDefaultInstance()
                onSynced(finalRealm, settings)
                finalRealm.close()

            } catch (e: Exception) {
                Log.e("SYNC", "Error during background sync: ${e.message}", e)
            } finally {
                _syncState.postValue(false)
                cleanupBackgroundSync()
                Log.d("SYNC", "Background sync completed.")
            }
        }
    }

    fun cancelBackgroundSync() {
        backgroundSync?.cancel()
        backgroundSync = null
    }

    private fun handleException(message: String?) {
        if (listener != null) {
            isSyncing = false
            MainApplication.syncFailedCount++
            listener?.onSyncFailed(message)
        }
    }

    private fun resourceTransactionSync(backgroundRealm: Realm? = null) {
        val apiInterface = client?.create(ApiInterface::class.java)
        try {
            if (backgroundRealm != null) {
                syncResource(apiInterface, backgroundRealm)
            } else {
                syncResource(apiInterface)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun syncResource(dbClient: ApiInterface?, backgroundRealm: Realm? = null) {
        val realmInstance = backgroundRealm ?: mRealm
        val newIds: MutableList<String?> = ArrayList()
        val allDocs = dbClient?.getJsonObject(Utilities.header, "${Utilities.getUrl()}/resources/_all_docs?include_doc=false")
        val all = allDocs?.execute()
        val rows = getJsonArray("rows", all?.body())

        // Log the total number of resources
        val totalResources = rows.size()
        Log.d("SYNC", "Total number of resources to sync: $totalResources")

        val keys: MutableList<String> = ArrayList()
        var processedCount = 0

        for (i in 0 until rows.size()) {
            val `object` = rows[i].asJsonObject
            if (!TextUtils.isEmpty(getString("id", `object`))) keys.add(getString("key", `object`))

            if (i == rows.size() - 1 || keys.size == 1000) {
                val obj = JsonObject()
                obj.add("keys", Gson().fromJson(Gson().toJson(keys), JsonArray::class.java))
                val response = dbClient?.findDocs(Utilities.header, "application/json", "${Utilities.getUrl()}/resources/_all_docs?include_docs=true", obj)?.execute()

                if (response?.body() != null) {
                    val docRows = getJsonArray("rows", response.body())
                    processedCount += docRows.size()

                    // Log progress
                    Log.d("SYNC", "Processing batch of ${docRows.size()} resources ($processedCount/$totalResources)")

                    // Create arrays for all, small, and large docs
                    val allDocsArray = JsonArray()
                    val smallDocsArray = JsonArray()
                    val largeDocsArray = JsonArray()
                    val allUserIds = ArrayList<String?>()

                    // First pass: categorize documents by size
                    for (j in 0 until docRows.size()) {
                        val docObj = docRows[j].asJsonObject
                        val document = getJsonObject("doc", docObj)
                        val id = getString("_id", document)

                        if (!id.startsWith("_design")) {
                            newIds.add(id)
                            allDocsArray.add(document)
                            allUserIds.add(null) // No specific userId for these docs

                            // Categorize by size
                            val docSize = document.toString().length
                            val hasAttachments = document.has("_attachments")
                            var totalAttachmentSize = 0L

                            if (hasAttachments) {
                                val attachments = document["_attachments"].asJsonObject
                                attachments.entrySet().forEach { (_, value) ->
                                    val attachmentObj = value.asJsonObject
                                    totalAttachmentSize += attachmentObj.get("length")?.asLong ?: 0
                                }
                            }

                            if (docSize > 10000 || totalAttachmentSize > 500000) {
                                largeDocsArray.add(document)
                            } else {
                                smallDocsArray.add(document)
                            }
                        }
                    }

                    Log.d("DocAnalysis", "Total docs: ${allDocsArray.size()}, Small docs: ${smallDocsArray.size()}, Large docs: ${largeDocsArray.size()}")

                    // Create corresponding user ID lists
                    val smallUserIds = ArrayList<String?>()
                    val largeUserIds = ArrayList<String?>()

                    // Fill with nulls to match doc counts
                    for (j in 0 until smallDocsArray.size()) {
                        smallUserIds.add(null)
                    }

                    for (j in 0 until largeDocsArray.size()) {
                        largeUserIds.add(null)
                    }

                    // Process small docs with normal batch size
                    if (smallDocsArray.size() > 0) {
                        Log.d("SYNC", "Processing ${smallDocsArray.size()} small documents with normal batch size")
                        batchInsertMyLibrary(smallUserIds, smallDocsArray, realmInstance)
                    }

                    // Process large docs with smaller batch size
                    if (largeDocsArray.size() > 0) {
                        Log.d("SYNC", "Processing ${largeDocsArray.size()} large documents with smaller batch size")

                        // Temporarily adjust batch size for large docs
                        val origBatchSize = BATCH_SIZE
                        val SMALLER_BATCH_SIZE = 20

                        try {
                            // Assuming BATCH_SIZE is accessible and mutable
                            BATCH_SIZE = SMALLER_BATCH_SIZE
                            batchInsertMyLibrary(largeUserIds, largeDocsArray, realmInstance)
                        } finally {
                            // Restore original batch size
                            BATCH_SIZE = origBatchSize
                        }
                    }
                }
                keys.clear()
            }
        }

        Log.d("SYNC", "Resource sync completed: $processedCount resources processed")

        // Make sure we're not in a transaction before calling removeDeletedResource
        if (realmInstance.isInTransaction) {
            realmInstance.commitTransaction()
        }

        // Clean up deleted resources
        removeDeletedResource(newIds, realmInstance)
    }

//    @Throws(IOException::class)
//    private fun syncResource(dbClient: ApiInterface?, backgroundRealm: Realm? = null) {
//        val realmInstance = backgroundRealm ?: mRealm
//        val newIds: MutableList<String?> = ArrayList()
//        val allDocs = dbClient?.getJsonObject(Utilities.header, "${Utilities.getUrl()}/resources/_all_docs?include_doc=false")
//        val all = allDocs?.execute()
//        val rows = getJsonArray("rows", all?.body())
//        val keys: MutableList<String> = ArrayList()
//        for (i in 0 until rows.size()) {
//            val `object` = rows[i].asJsonObject
//            if (!TextUtils.isEmpty(getString("id", `object`))) keys.add(getString("key", `object`))
//            if (i == rows.size() - 1 || keys.size == 1000) {
//                val obj = JsonObject()
//                obj.add("keys", Gson().fromJson(Gson().toJson(keys), JsonArray::class.java))
//                val response = dbClient?.findDocs(Utilities.header, "application/json", "${Utilities.getUrl()}/resources/_all_docs?include_docs=true", obj)?.execute()
//                if (response?.body() != null) {
//                    val ids: List<String?> = save(getJsonArray("rows", response.body()), realmInstance)
//                    newIds.addAll(ids)
//                }
//                keys.clear()
//            }
//        }
//        removeDeletedResource(newIds, realmInstance)
//    }

    private fun myLibraryTransactionSync(backgroundRealm: Realm? = null) {
        val apiInterface = client?.create(ApiInterface::class.java)
        try {
            val res = apiInterface?.getDocuments(Utilities.header, "${Utilities.getUrl()}/shelf/_all_docs")?.execute()?.body()
            for (i in res?.rows!!.indices) {
                shelfDoc = res.rows!![i]
                if (backgroundRealm != null) {
                    populateShelfItems(apiInterface, backgroundRealm)
                } else {
                    populateShelfItems(apiInterface)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun populateShelfItems(apiInterface: ApiInterface, backgroundRealm: Realm? = null) {
        try {
            val jsonDoc = apiInterface.getJsonObject(Utilities.header, "${Utilities.getUrl()}/shelf/${shelfDoc?.id}").execute().body()
            for (i in Constants.shelfDataList.indices) {
                val shelfData = Constants.shelfDataList[i]
                val array = getJsonArray(shelfData.key, jsonDoc)
                if (backgroundRealm != null) {
                    memberShelfData(array, shelfData, backgroundRealm)
                } else {
                    memberShelfData(array, shelfData)
                }
            }
        } catch (err: Exception) {
            err.printStackTrace()
        }
    }

    private fun memberShelfData(array: JsonArray, shelfData: ShelfData, backgroundRealm: Realm? = null) {
        if (array.size() > 0) {
            triggerInsert(shelfData.categoryKey, shelfData.type)
            if (backgroundRealm != null) {
                check(array, backgroundRealm)
            } else {
                check(array)
            }
        }
    }

    private fun triggerInsert(categoryId: String, categoryDBName: String) {
        stringArray[0] = shelfDoc?.id
        stringArray[1] = categoryId
        stringArray[2] = categoryDBName
    }

    private fun check(arrayCategoryIds: JsonArray, backgroundRealm: Realm? = null) {
        for (x in 0 until arrayCategoryIds.size()) {
            if (arrayCategoryIds[x] is JsonNull) {
                continue
            }
            if (backgroundRealm != null) {
                validateDocument(arrayCategoryIds, x, backgroundRealm)
            } else {
                validateDocument(arrayCategoryIds, x)
            }
        }
    }

    private fun validateDocument(arrayCategoryIds: JsonArray, x: Int, backgroundRealm: Realm? = null) {
        val apiInterface = client?.create(ApiInterface::class.java)
        try {
            val resourceDoc = apiInterface?.getJsonObject(Utilities.header,  "${Utilities.getUrl()}/${stringArray[2]}/${arrayCategoryIds[x].asString}")?.execute()?.body()
            if (backgroundRealm != null) {
                resourceDoc?.let { triggerInsert(stringArray, it, backgroundRealm)}
            } else {
                resourceDoc?.let { triggerInsert(stringArray, it) }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun triggerInsert(stringArray: Array<String?>, resourceDoc: JsonObject, backgroundRealm: Realm? = null) {
        when (stringArray[2]) {
            "resources" -> {
                // Add to buffer instead of processing immediately
                userIdBuffer.add(stringArray[0])
                resourceBuffer.add(resourceDoc)

                // Process the batch when buffer is full or on forced flush
                if (resourceBuffer.size >= RESOURCE_BUFFER_SIZE) {
                    flushResourceBuffer(backgroundRealm ?: mRealm)
                }
            }

//            "resources" ->
//                if (backgroundRealm != null) {
//                    insertMyLibrary(stringArray[0], resourceDoc, backgroundRealm)
//                } else {
//                    insertMyLibrary(stringArray[0], resourceDoc, mRealm)
//                }

            "meetups" ->
                if (backgroundRealm != null) {
                    insert(backgroundRealm, resourceDoc)
                } else {
                    insert(mRealm, resourceDoc)
                }

            "courses" -> {
                if (backgroundRealm != null) {
                    insertMyCourses(stringArray[0], resourceDoc, backgroundRealm)
                } else {
                    if (!mRealm.isInTransaction) {
                        mRealm.beginTransaction()
                    }
                    insertMyCourses(stringArray[0], resourceDoc, mRealm)
                    if (mRealm.isInTransaction) {
                        mRealm.commitTransaction()
                    }
                }
            }

            "teams" ->
                if (backgroundRealm != null) {
                    insertMyTeams(resourceDoc, backgroundRealm)
                } else {
                    insertMyTeams(resourceDoc, mRealm)
                }
        }
        saveConcatenatedLinksToPrefs()
    }

    private fun flushResourceBuffer(realm: Realm) {
        if (resourceBuffer.isEmpty()) return

        Log.d("SYNC", "Flushing resource buffer with ${resourceBuffer.size} items")

        // Convert the ArrayList to JsonArray
        val jsonArray = JsonArray()
        resourceBuffer.forEach { jsonArray.add(it) }

        // Process the batch
        batchInsertMyLibrary(userIdBuffer, jsonArray, realm)

        // Clear the buffers
        resourceBuffer.clear()
        userIdBuffer.clear()
    }

    companion object {
        private var ourInstance: SyncManager? = null
        val instance: SyncManager?
            get() {
                ourInstance = SyncManager(MainApplication.context)
                return ourInstance
            }
    }
}
