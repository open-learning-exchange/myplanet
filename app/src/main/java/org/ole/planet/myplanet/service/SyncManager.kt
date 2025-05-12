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
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.insertMyLibrary
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
import org.ole.planet.myplanet.datamanager.ApiClient
import org.ole.planet.myplanet.model.DocumentResponse
import org.ole.planet.myplanet.utilities.JsonUtils.getJsonObject
import org.ole.planet.myplanet.utilities.SyncTimeLogger
import java.util.concurrent.atomic.AtomicInteger

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
            val logger = SyncTimeLogger.getInstance()
            logger.startLogging()

            initializeSync()

            runBlocking {
                val syncJobs = listOf(
                    async {
                        logger.startProcess("tablet_users_sync")
                        val tabletUserItems = measureAndCount {
                            TransactionSyncManager.syncDb(mRealm, "tablet_users")
                        }
                        logger.endProcess("tablet_users_sync", tabletUserItems)
                    },
                    async {
                        logger.startProcess("library_sync")
                        val libraryItems = measureAndCount {
                            myLibraryTransactionSync()
                        }
                        logger.endProcess("library_sync", libraryItems)
                    },
                    async {
                        logger.startProcess("courses_sync")
                        val courseItems = measureAndCount {
                            TransactionSyncManager.syncDb(mRealm, "courses")
                        }
                        logger.endProcess("courses_sync", courseItems)
                    },
                    async {
                        logger.startProcess("exams_sync")
                        val examItems = measureAndCount {
                            TransactionSyncManager.syncDb(mRealm, "exams")
                        }
                        logger.endProcess("exams_sync", examItems)
                    },
                    async {
                        logger.startProcess("ratings_sync")
                        val ratingItems = measureAndCount {
                            TransactionSyncManager.syncDb(mRealm, "ratings")
                        }
                        logger.endProcess("ratings_sync", ratingItems)
                    },
                    async {
                        logger.startProcess("courses_progress_sync")
                        val progressItems = measureAndCount {
                            TransactionSyncManager.syncDb(mRealm, "courses_progress")
                        }
                        logger.endProcess("courses_progress_sync", progressItems)
                    },
                    async {
                        logger.startProcess("achievements_sync")
                        val achievementItems = measureAndCount {
                            TransactionSyncManager.syncDb(mRealm, "achievements")
                        }
                        logger.endProcess("achievements_sync", achievementItems)
                    },
                    async {
                        logger.startProcess("tags_sync")
                        val tagItems = measureAndCount {
                            TransactionSyncManager.syncDb(mRealm, "tags")
                        }
                        logger.endProcess("tags_sync", tagItems)
                    },
                    async {
                        logger.startProcess("submissions_sync")
                        val submissionItems = measureAndCount {
                            TransactionSyncManager.syncDb(mRealm, "submissions")
                        }
                        logger.endProcess("submissions_sync", submissionItems)
                    },
                    async {
                        logger.startProcess("news_sync")
                        val newsItems = measureAndCount {
                            TransactionSyncManager.syncDb(mRealm, "news")
                        }
                        logger.endProcess("news_sync", newsItems)
                    },
                    async {
                        logger.startProcess("feedback_sync")
                        val feedbackItems = measureAndCount {
                            TransactionSyncManager.syncDb(mRealm, "feedback")
                        }
                        logger.endProcess("feedback_sync", feedbackItems)
                    },
                    async {
                        logger.startProcess("teams_sync")
                        val teamItems = measureAndCount {
                            TransactionSyncManager.syncDb(mRealm, "teams")
                        }
                        logger.endProcess("teams_sync", teamItems)
                    },
                    async {
                        logger.startProcess("tasks_sync")
                        val taskItems = measureAndCount {
                            TransactionSyncManager.syncDb(mRealm, "tasks")
                        }
                        logger.endProcess("tasks_sync", taskItems)
                    },
                    async {
                        logger.startProcess("login_activities_sync")
                        val loginItems = measureAndCount {
                            TransactionSyncManager.syncDb(mRealm, "login_activities")
                        }
                        logger.endProcess("login_activities_sync", loginItems)
                    },
                    async {
                        logger.startProcess("meetups_sync")
                        val meetupItems = measureAndCount {
                            TransactionSyncManager.syncDb(mRealm, "meetups")
                        }
                        logger.endProcess("meetups_sync", meetupItems)
                    },
                    async {
                        logger.startProcess("health_sync")
                        val healthItems = measureAndCount {
                            TransactionSyncManager.syncDb(mRealm, "health")
                        }
                        logger.endProcess("health_sync", healthItems)
                    },
                    async {
                        logger.startProcess("certifications_sync")
                        val certItems = measureAndCount {
                            TransactionSyncManager.syncDb(mRealm, "certifications")
                        }
                        logger.endProcess("certifications_sync", certItems)
                    },
                    async {
                        logger.startProcess("team_activities_sync")
                        val teamActivityItems = measureAndCount {
                            TransactionSyncManager.syncDb(mRealm, "team_activities")
                        }
                        logger.endProcess("team_activities_sync", teamActivityItems)
                    },
                    async {
                        logger.startProcess("chat_history_sync")
                        val chatItems = measureAndCount {
                            TransactionSyncManager.syncDb(mRealm, "chat_history")
                        }
                        logger.endProcess("chat_history_sync", chatItems)
                    }
                )

                syncJobs.awaitAll()
            }

            logger.startProcess("admin_sync")
            val adminItems = measureAndCount {
                ManagerSync.instance?.syncAdmin()
            }
            logger.endProcess("admin_sync", adminItems)

            logger.startProcess("resource_sync")
            val resourceItems = measureAndCount {
                resourceTransactionSync()
            }
            logger.endProcess("resource_sync", resourceItems)

            logger.startProcess("on_synced")
            onSynced(mRealm, settings)
            logger.endProcess("on_synced")
            mRealm.close()

            logger.stopLogging()
        } catch (err: Exception) {
            err.printStackTrace()
            handleException(err.message)
        } finally {
            destroy()
        }
    }

    // Helper function to measure execution time and count processed items
    private fun <T> measureAndCount(block: () -> T): Int {
        val itemCounter = AtomicInteger(0)

        // Hook into the item processing to count items
        // This is a placeholder - in a real implementation,
        // you would need to modify your sync methods to return counts

        block()

        // For now, returning 0 as we don't have a way to count items yet
        return itemCounter.get()
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
        val logger = SyncTimeLogger.getInstance()
        logger.startProcess("resource_sync")
        var processedItems = 0

        try {
            // Get enhanced API client with longer timeouts
            val apiInterface = ApiClient.getEnhancedClient()
            val realmInstance = backgroundRealm ?: mRealm
            val newIds: MutableList<String?> = ArrayList()

            // First get the count of resources
            var totalRows = 0
            ApiClient.executeWithRetry {
                apiInterface.getJsonObject(
                    Utilities.header,
                    "${Utilities.getUrl()}/resources/_all_docs?limit=0"
                ).execute()
            }?.let { response ->
                response.body()?.let { body ->
                    if (body.has("total_rows")) {
                        totalRows = body.get("total_rows").asInt
                    }
                }
            }

            Log.d("SYNC", "Total resources to sync: $totalRows")

            // Use smaller batch size to avoid timeouts
            val batchSize = 200
            var skip = 0

            while (skip < totalRows || (totalRows == 0 && skip == 0)) {
                try {
                    // Get a batch of resources
                    var response: JsonObject? = null
                    ApiClient.executeWithRetry {
                        apiInterface.getJsonObject(
                            Utilities.header,
                            "${Utilities.getUrl()}/resources/_all_docs?include_docs=true&limit=$batchSize&skip=$skip"
                        ).execute()
                    }?.let {
                        response = it.body()
                    }

                    if (response == null) {
                        Log.e("SYNC", "Failed to get resources batch at offset $skip")
                        skip += batchSize // Skip to next batch despite error
                        continue
                    }

                    val rows = getJsonArray("rows", response)

                    if (rows.size() == 0) {
                        // No more resources to fetch
                        break
                    }

                    // Process each document individually with proper transaction handling
                    for (i in 0 until rows.size()) {
                        val rowObj = rows[i].asJsonObject
                        if (rowObj.has("doc")) {
                            val doc = getJsonObject("doc", rowObj)
                            val id = getString("_id", doc)

                            if (!id.startsWith("_design")) {
                                try {
                                    // Start tracking this resource
                                    logger.startResourceSync(id)

                                    // Begin transaction for each document
                                    realmInstance.beginTransaction()

                                    // Create a single-element array for compatibility with existing save method
                                    val singleDocArray = JsonArray()
                                    singleDocArray.add(doc)

                                    // Save the document
                                    val ids = save(singleDocArray, realmInstance)
                                    if (ids.isNotEmpty()) {
                                        newIds.addAll(ids)
                                        processedItems++
                                    }

                                    // Commit transaction
                                    if (realmInstance.isInTransaction) {
                                        realmInstance.commitTransaction()
                                    }

                                    // End tracking for this resource
                                    logger.endResourceSync(id)
                                } catch (e: Exception) {
                                    // Cancel transaction if error occurs
                                    if (realmInstance.isInTransaction) {
                                        realmInstance.cancelTransaction()
                                    }
                                    Log.e("SYNC", "Error saving resource: ${e.message}")

                                    // Still end the tracking even if there was an error
                                    logger.endResourceSync(id)
                                }
                            }
                        }
                    }

                    skip += rows.size()

                    // Log progress
                    if (totalRows > 0) {
                        val progress = (skip * 100.0 / totalRows).toInt()
                        Log.d("SYNC", "Resource sync progress: $progress% ($skip/$totalRows)")
                    } else {
                        Log.d("SYNC", "Resource sync progress: processed $skip resources")
                    }

                    // Periodically save progress to enable resuming if sync is interrupted
                    val settings = MainApplication.context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                    )
                    settings.edit {
                        putLong("ResourceLastSyncTime", System.currentTimeMillis())
                        putInt("ResourceSyncPosition", skip)
                    }

                } catch (e: Exception) {
                    Log.e("SYNC", "Error processing resource batch: ${e.message}")
                    skip += batchSize // Skip to next batch despite error
                }
            }

            // Remove deleted resources in its own transaction
            try {
                // Start tracking this resource operation
                logger.startResourceSync("remove_deleted_resources")

                // Start transaction
                realmInstance.beginTransaction()

                // Call the existing method to remove deleted resources
                removeDeletedResource(newIds, realmInstance)

                // Commit transaction
                if (realmInstance.isInTransaction) {
                    realmInstance.commitTransaction()
                }

                // End tracking this resource operation
                logger.endResourceSync("remove_deleted_resources")
            } catch (e: Exception) {
                // Cancel transaction if error occurs
                if (realmInstance.isInTransaction) {
                    realmInstance.cancelTransaction()
                }
                Log.e("SYNC", "Error removing deleted resources: ${e.message}")

                // Still end tracking even if there was an error
                logger.endResourceSync("remove_deleted_resources")
            }

            logger.endProcess("resource_sync", processedItems)
        } catch (e: Exception) {
            Log.e("SYNC", "Error in resourceTransactionSync: ${e.message}", e)
            logger.endProcess("resource_sync", processedItems)
        }
    }

    @Throws(IOException::class)
    private fun syncResourceThreadSafe(dbClient: ApiInterface?, backgroundRealm: Realm? = null) {
        val realmInstance = backgroundRealm ?: mRealm
        val newIds: MutableList<String?> = ArrayList()

        // Get all resource documents
        val allDocs = dbClient?.getJsonObject(
            Utilities.header,
            "${Utilities.getUrl()}/resources/_all_docs?include_doc=false"
        )
        val all = allDocs?.execute()
        val rows = getJsonArray("rows", all?.body())

        // Collect all keys first
        val allKeys = mutableListOf<String>()
        for (i in 0 until rows.size()) {
            val `object` = rows[i].asJsonObject
            if (!TextUtils.isEmpty(getString("id", `object`))) {
                allKeys.add(getString("key", `object`))
            }
        }

        // Process in larger batches (2000 instead of 1000)
        val batchSize = 2000
        allKeys.chunked(batchSize).forEach { keys ->
            if (keys.isNotEmpty()) {
                val obj = JsonObject()
                obj.add("keys", Gson().fromJson(Gson().toJson(keys), JsonArray::class.java))

                val response = dbClient?.findDocs(
                    Utilities.header,
                    "application/json",
                    "${Utilities.getUrl()}/resources/_all_docs?include_docs=true",
                    obj
                )?.execute()

                if (response?.body() != null) {
                    // Save is called on the same thread that created the Realm instance
                    val ids: List<String?> = save(getJsonArray("rows", response.body()), realmInstance)
                    newIds.addAll(ids)
                }
            }
        }

        // Remove deleted resources
        removeDeletedResource(newIds, realmInstance)
    }

    private fun myLibraryTransactionSync(backgroundRealm: Realm? = null) {
        val logger = SyncTimeLogger.getInstance()
        logger.startProcess("library_sync")
        var processedItems = 0

        try {
            // Get enhanced API client with longer timeouts
            val apiInterface = ApiClient.getEnhancedClient()
            val realmInstance = backgroundRealm ?: mRealm

            // Get all shelf documents with retry
            var shelfResponse: DocumentResponse? = null
            ApiClient.executeWithRetry {
                apiInterface.getDocuments(
                    Utilities.header,
                    "${Utilities.getUrl()}/shelf/_all_docs?include_docs=true"
                ).execute()
            }?.let {
                shelfResponse = it.body()
            }

            if (shelfResponse?.rows == null || shelfResponse?.rows!!.isEmpty()) {
                logger.endProcess("library_sync", 0)
                return
            }

            // Process each shelf
            for (row in shelfResponse?.rows!!) {
                val shelfId = row.id
                logger.startResourceSync("shelf_$shelfId")

                // Get shelf document with all content types
                var shelfDoc: JsonObject? = null
                ApiClient.executeWithRetry {
                    apiInterface.getJsonObject(
                        Utilities.header,
                        "${Utilities.getUrl()}/shelf/$shelfId"
                    ).execute()
                }?.let {
                    shelfDoc = it.body()
                }

                if (shelfDoc == null) {
                    logger.endResourceSync("shelf_$shelfId")
                    continue
                }

                // Process each shelf data type
                for (shelfData in Constants.shelfDataList) {
                    val dataTypeKey = "${shelfId}_${shelfData.type}"
                    logger.startResourceSync(dataTypeKey)

                    val array = getJsonArray(shelfData.key, shelfDoc)
                    if (array.size() == 0) {
                        logger.endResourceSync(dataTypeKey)
                        continue
                    }

                    // Set up the category information
                    stringArray[0] = shelfId
                    stringArray[1] = shelfData.categoryKey
                    stringArray[2] = shelfData.type

                    // Filter out null values
                    val validIds = mutableListOf<String>()
                    for (i in 0 until array.size()) {
                        if (array[i] !is JsonNull) {
                            validIds.add(array[i].asString)
                        }
                    }

                    if (validIds.isEmpty()) {
                        logger.endResourceSync(dataTypeKey)
                        continue
                    }

                    // Process in smaller batches to avoid timeouts
                    val batchSize = 50

                    for (i in 0 until validIds.size step batchSize) {
                        val end = minOf(i + batchSize, validIds.size)
                        val batch = validIds.subList(i, end)
                        val batchKey = "${dataTypeKey}_batch_$i"
                        logger.startResourceSync(batchKey)

                        try {
                            // Fetch documents in bulk
                            val keysObject = JsonObject()
                            keysObject.add("keys", Gson().fromJson(Gson().toJson(batch), JsonArray::class.java))

                            var response: JsonObject? = null
                            ApiClient.executeWithRetry {
                                apiInterface.findDocs(
                                    Utilities.header,
                                    "application/json",
                                    "${Utilities.getUrl()}/${shelfData.type}/_all_docs?include_docs=true",
                                    keysObject
                                ).execute()
                            }?.let {
                                response = it.body()
                            }

                            if (response == null) {
                                logger.endResourceSync(batchKey)
                                continue
                            }

                            val rows = getJsonArray("rows", response)

                            // Process each document individually with proper transaction management
                            for (j in 0 until rows.size()) {
                                val rowObj = rows[j].asJsonObject
                                if (rowObj.has("doc")) {
                                    val doc = getJsonObject("doc", rowObj)
                                    val docId = getString("_id", doc)
                                    logger.startResourceSync("${shelfData.type}_$docId")

                                    try {
                                        // Begin transaction for each document
                                        realmInstance.beginTransaction()

                                        // Insert based on type
                                        when (shelfData.type) {
                                            "resources" -> insertMyLibrary(shelfId, doc, realmInstance)
                                            "meetups" -> insert(realmInstance, doc)
                                            "courses" -> insertMyCourses(shelfId, doc, realmInstance)
                                            "teams" -> insertMyTeams(doc, realmInstance)
                                        }

                                        // Commit transaction
                                        if (realmInstance.isInTransaction) {
                                            realmInstance.commitTransaction()
                                            processedItems++
                                        }
                                    } catch (e: Exception) {
                                        // Cancel transaction if error occurs
                                        if (realmInstance.isInTransaction) {
                                            realmInstance.cancelTransaction()
                                        }
                                        Log.e("SYNC", "Error in document transaction: ${e.message}")
                                    } finally {
                                        logger.endResourceSync("${shelfData.type}_$docId")
                                    }
                                }
                            }

                            Log.d("SYNC", "Processed ${rows.size()} ${shelfData.type} items for shelf $shelfId")
                        } catch (e: Exception) {
                            Log.e("SYNC", "Error processing batch of ${shelfData.type}: ${e.message}")
                        } finally {
                            logger.endResourceSync(batchKey)
                        }
                    }

                    logger.endResourceSync(dataTypeKey)
                }

                logger.endResourceSync("shelf_$shelfId")
            }

            // Save concatenated links
            logger.startResourceSync("save_concatenated_links")
            saveConcatenatedLinksToPrefs()
            logger.endResourceSync("save_concatenated_links")

            logger.endProcess("library_sync", processedItems)
        } catch (e: Exception) {
            Log.e("SYNC", "Error in myLibraryTransactionSync: ${e.message}", e)
            logger.endProcess("library_sync", processedItems)
        }
    }

    private fun populateShelfItemsOptimized(apiInterface: ApiInterface?, realmInstance: Realm) {
        try {
            // Get the complete shelf document
            val jsonDoc = apiInterface?.getJsonObject(
                Utilities.header,
                "${Utilities.getUrl()}/shelf/${shelfDoc?.id}"
            )?.execute()?.body()

            // Pre-fetch all documents that will be needed
            val allIdsToFetch = mutableMapOf<String, MutableList<String>>()

            // First pass - collect all IDs that need to be fetched, grouped by type
            for (shelfData in Constants.shelfDataList) {
                val array = getJsonArray(shelfData.key, jsonDoc)
                if (array.size() > 0) {
                    val ids = mutableListOf<String>()
                    for (x in 0 until array.size()) {
                        if (array[x] !is JsonNull) {
                            ids.add(array[x].asString)
                        }
                    }
                    if (ids.isNotEmpty()) {
                        allIdsToFetch[shelfData.type] = ids
                    }
                }
            }

            // Second pass - fetch documents in batches for each type
            for ((type, ids) in allIdsToFetch) {
                // Process in batches of 100
                val batchSize = 100
                ids.chunked(batchSize).forEach { batch ->
                    val obj = JsonObject()
                    obj.add("keys", Gson().fromJson(Gson().toJson(batch), JsonArray::class.java))

                    val response = apiInterface?.findDocs(
                        Utilities.header,
                        "application/json",
                        "${Utilities.getUrl()}/$type/_all_docs?include_docs=true",
                        obj
                    )?.execute()

                    if (response?.body() != null) {
                        val rows = getJsonArray("rows", response.body())

                        // Process all documents in one transaction
                        realmInstance.executeTransaction { realm ->
                            for (j in 0 until rows.size()) {
                                val rowObj = rows[j].asJsonObject
                                val doc = getJsonObject("doc", rowObj)

                                // Set up string array for proper insertion
                                stringArray[0] = shelfDoc?.id
                                stringArray[1] = "" // Set appropriate category ID if needed
                                stringArray[2] = type

                                // Insert based on type
                                when (type) {
                                    "resources" -> insertMyLibrary(shelfDoc?.id, doc, realm)
                                    "meetups" -> insert(realm, doc)
                                    "courses" -> insertMyCourses(shelfDoc?.id, doc, realm)
                                    "teams" -> insertMyTeams(doc, realm)
                                }
                            }
                        }
                    }
                }
            }

            // Save concatenated links
            saveConcatenatedLinksToPrefs()

        } catch (e: Exception) {
            Log.e("SYNC", "Error in populateShelfItemsOptimized: ${e.message}", e)
        }
    }

//    private fun resourceTransactionSync(backgroundRealm: Realm? = null) {
//        val apiInterface = client?.create(ApiInterface::class.java)
//        try {
//            if (backgroundRealm != null) {
//                syncResource(apiInterface, backgroundRealm)
//            } else {
//                syncResource(apiInterface)
//            }
//        } catch (e: IOException) {
//            e.printStackTrace()
//        }
//    }

    @Throws(IOException::class)
    private fun syncResource(dbClient: ApiInterface?, backgroundRealm: Realm? = null) {
        val realmInstance = backgroundRealm ?: mRealm
        val newIds: MutableList<String?> = ArrayList()
        val allDocs = dbClient?.getJsonObject(Utilities.header, "${Utilities.getUrl()}/resources/_all_docs?include_doc=false")
        val all = allDocs?.execute()
        val rows = getJsonArray("rows", all?.body())
        val keys: MutableList<String> = ArrayList()
        for (i in 0 until rows.size()) {
            val `object` = rows[i].asJsonObject
            if (!TextUtils.isEmpty(getString("id", `object`))) keys.add(getString("key", `object`))
            if (i == rows.size() - 1 || keys.size == 1000) {
                val obj = JsonObject()
                obj.add("keys", Gson().fromJson(Gson().toJson(keys), JsonArray::class.java))
                val response = dbClient?.findDocs(Utilities.header, "application/json", "${Utilities.getUrl()}/resources/_all_docs?include_docs=true", obj)?.execute()
                if (response?.body() != null) {
                    val ids: List<String?> = save(getJsonArray("rows", response.body()), realmInstance)
                    newIds.addAll(ids)
                }
                keys.clear()
            }
        }
        removeDeletedResource(newIds, realmInstance)
    }

//    private fun myLibraryTransactionSync(backgroundRealm: Realm? = null) {
//        val apiInterface = client?.create(ApiInterface::class.java)
//        try {
//            val res = apiInterface?.getDocuments(Utilities.header, "${Utilities.getUrl()}/shelf/_all_docs")?.execute()?.body()
//            for (i in res?.rows!!.indices) {
//                shelfDoc = res.rows!![i]
//                if (backgroundRealm != null) {
//                    populateShelfItems(apiInterface, backgroundRealm)
//                } else {
//                    populateShelfItems(apiInterface)
//                }
//            }
//        } catch (e: IOException) {
//            e.printStackTrace()
//        }
//    }

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
            "resources" ->
                if (backgroundRealm != null) {
                    insertMyLibrary(stringArray[0], resourceDoc, backgroundRealm)
                } else {
                    insertMyLibrary(stringArray[0], resourceDoc, mRealm)
                }

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

    companion object {
        private var ourInstance: SyncManager? = null
        val instance: SyncManager?
            get() {
                ourInstance = SyncManager(MainApplication.context)
                return ourInstance
            }
    }
}