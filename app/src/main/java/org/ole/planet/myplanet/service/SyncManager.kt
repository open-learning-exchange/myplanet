package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import io.realm.Realm
import kotlinx.coroutines.*
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.SyncListener
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
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.JsonUtils.getJsonArray
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.NotificationUtil.cancel
import org.ole.planet.myplanet.utilities.NotificationUtil.create
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Date
import androidx.core.content.edit
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.ole.planet.myplanet.datamanager.ApiClient
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.model.DocumentResponse
import org.ole.planet.myplanet.model.Rows
import org.ole.planet.myplanet.utilities.JsonUtils.getJsonObject
import org.ole.planet.myplanet.utilities.SyncTimeLogger
import java.util.concurrent.ConcurrentHashMap

class SyncManager private constructor(private val context: Context) {
    private var td: Thread? = null
    private val settings: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    lateinit var mRealm: Realm
    private var isSyncing = false
    private val stringArray = arrayOfNulls<String>(4)
    private var listener: SyncListener? = null
    private val dbService: DatabaseService = DatabaseService(context)
    private var backgroundSync: Job? = null
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val semaphore = Semaphore(5)
    private var betaSync = false

    fun start(listener: SyncListener?, type: String) {
        this.listener = listener
        if (!isSyncing) {
            settings.edit { remove("concatenated_links") }
            listener?.onSyncStarted()
            authenticateAndSync(type)
        }
    }

    private fun destroy() {
        if (betaSync) {
            cleanup()
        }
        cancelBackgroundSync()
        cancel(context, 111)
        isSyncing = false
        ourInstance = null
        settings.edit { putLong("LastSync", Date().time) }
        listener?.onSyncComplete()
        try {
            if (!betaSync) {
                if (::mRealm.isInitialized && !mRealm.isClosed) {
                    mRealm.close()
                    td?.interrupt()
                }
            } else {
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
        val isFastSync = settings.getBoolean("fastSync", false)
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
                        TransactionSyncManager.syncDb(mRealm, "tablet_users")
                        logger.endProcess("tablet_users_sync")
                    },
                    async {
                        logger.startProcess("library_sync")
                        myLibraryTransactionSync()
                        logger.endProcess("library_sync")
                    },
                    async { logger.startProcess("courses_sync")
                        TransactionSyncManager.syncDb(mRealm, "courses")
                        logger.endProcess("courses_sync")
                    },
                    async { logger.startProcess("exams_sync")
                        TransactionSyncManager.syncDb(mRealm, "exams")
                        logger.endProcess("exams_sync")
                    },
                    async { logger.startProcess("ratings_sync")
                        TransactionSyncManager.syncDb(mRealm, "ratings")
                        logger.endProcess("ratings_sync")
                    },
                    async { logger.startProcess("courses_progress_sync")
                        TransactionSyncManager.syncDb(mRealm, "courses_progress")
                        logger.endProcess("courses_progress_sync")
                    },
                    async { logger.startProcess("achievements_sync")
                        TransactionSyncManager.syncDb(mRealm, "achievements")
                        logger.endProcess("achievements_sync")
                    },
                    async { logger.startProcess("tags_sync")
                        TransactionSyncManager.syncDb(mRealm, "tags")
                        logger.endProcess("tags_sync")
                    },
                    async { logger.startProcess("submissions_sync")
                        TransactionSyncManager.syncDb(mRealm, "submissions")
                        logger.endProcess("submissions_sync")
                    },
                    async { logger.startProcess("news_sync")
                        TransactionSyncManager.syncDb(mRealm, "news")
                        logger.endProcess("news_sync")
                    },
                    async { logger.startProcess("feedback_sync")
                        TransactionSyncManager.syncDb(mRealm, "feedback")
                        logger.endProcess("feedback_sync")
                    },
                    async { logger.startProcess("teams_sync")
                        TransactionSyncManager.syncDb(mRealm, "teams")
                        logger.endProcess("teams_sync")
                    },
                    async { logger.startProcess("tasks_sync")
                        TransactionSyncManager.syncDb(mRealm, "tasks")
                        logger.endProcess("tasks_sync")
                    },
                    async { logger.startProcess("login_activities_sync")
                        TransactionSyncManager.syncDb(mRealm, "login_activities")
                        logger.endProcess("login_activities_sync")
                    },
                    async { logger.startProcess("meetups_sync")
                        TransactionSyncManager.syncDb(mRealm, "meetups")
                        logger.endProcess("meetups_sync")
                    },
                    async { logger.startProcess("health_sync")
                        TransactionSyncManager.syncDb(mRealm, "health")
                        logger.endProcess("health_sync")
                    },
                    async { logger.startProcess("certifications_sync")
                        TransactionSyncManager.syncDb(mRealm, "certifications")
                        logger.endProcess("certifications_sync")
                    },
                    async { logger.startProcess("team_activities_sync")
                        TransactionSyncManager.syncDb(mRealm, "team_activities")
                        logger.endProcess("team_activities_sync")
                    },
                    async { logger.startProcess("chat_history_sync")
                        TransactionSyncManager.syncDb(mRealm, "chat_history")
                        logger.endProcess("chat_history_sync")
                    }
                )
                syncJobs.awaitAll()
            }

            logger.startProcess("admin_sync")
            ManagerSync.instance?.syncAdmin()
            logger.endProcess("admin_sync")

            logger.startProcess("resource_sync")
            resourceTransactionSync()
            logger.endProcess("resource_sync")

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

    private fun startFastSync() {
        betaSync = true
        var mainRealm: Realm? = null
        try {
            val logger = SyncTimeLogger.getInstance()
            logger.startLogging()

            initializeSync()
            mainRealm = dbService.realmInstance
            runBlocking {
                // Phase 1: Critical syncs (sequential)
                async {
                    syncWithSemaphore("tablet_users") {
                        safeRealmOperation { realm ->
                            TransactionSyncManager.syncDb(realm, "tablet_users")
                        }
                    }
                }.await()

                // Phase 2: Major syncs in parallel (this is the key optimization)
                val majorSyncs = listOf(
                    async(Dispatchers.IO) { fastResourceTransactionSync() },
                    async(Dispatchers.IO) { fastMyLibraryTransactionSync() }
                )
                majorSyncs.awaitAll()

                // Phase 3: Remaining syncs in parallel
                val remainingSyncs = listOf(
                    async { syncWithSemaphore("courses") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "courses") }
                    }},
                    async { syncWithSemaphore("exams") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "exams") }
                    }},
                    async { syncWithSemaphore("ratings") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "ratings") }
                    }},
                    async { syncWithSemaphore("achievements") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "achievements") }
                    }},
                    async { syncWithSemaphore("tags") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "tags") }
                    }},
                    async { syncWithSemaphore("news") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "news") }
                    }},
                    async { syncWithSemaphore("feedback") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "feedback") }
                    }},
                    async { syncWithSemaphore("teams") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "teams") }
                    }},
                    async { syncWithSemaphore("meetups") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "meetups") }
                    }},
                    async { syncWithSemaphore("health") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "health") }
                    }},
                    async { syncWithSemaphore("certifications") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "certifications") }
                    }},
                    async { syncWithSemaphore("courses_progress") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "courses_progress") }
                    }},
                    async { syncWithSemaphore("submissions") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "submissions") }
                    }},
                    async { syncWithSemaphore("tasks") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "tasks") }
                    }},
                    async { syncWithSemaphore("login_activities") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "login_activities") }
                    }},
                    async { syncWithSemaphore("team_activities") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "team_activities") }
                    }},
                    async { syncWithSemaphore("chat_history") {
                        safeRealmOperation { realm -> TransactionSyncManager.syncDb(realm, "chat_history") }
                    }}
                )
                remainingSyncs.awaitAll()
            }

            logger.startProcess("admin_sync")
            ManagerSync.instance?.syncAdmin()
            logger.endProcess("admin_sync")

            logger.startProcess("on_synced")
            onSynced(mainRealm, settings)
            logger.endProcess("on_synced")

            logger.stopLogging()

        } catch (err: Exception) {
            err.printStackTrace()
            handleException(err.message)
        } finally {
            mainRealm?.close()
            destroy()
        }
    }

    private fun cleanupMainSync() {
        cancel(context, 111)
        isSyncing = false
        if (!betaSync) {
            if (::mRealm.isInitialized && !mRealm.isClosed) {
                mRealm.close()
                td?.interrupt()
            }
        } else {
            td?.interrupt()
        }
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

    fun cancelBackgroundSync() {
        backgroundSync?.cancel()
        backgroundSync = null
    }

    private suspend fun syncWithSemaphore(name: String, syncOperation: suspend () -> Unit) {
        semaphore.withPermit {
            val logger = SyncTimeLogger.getInstance()
            logger.startProcess("${name}_sync")
            try {
                syncOperation()
            } finally {
                logger.endProcess("${name}_sync")
            }
        }
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
        val functionStartTime = System.currentTimeMillis()
        Log.d("ResourceSync", "Starting resource sync at ${Date(functionStartTime)}")

        try {
            val apiClientStartTime = System.currentTimeMillis()
            val apiInterface = ApiClient.getEnhancedClient()
            val realmInstance = backgroundRealm ?: mRealm
            val newIds: MutableList<String?> = ArrayList()
            Log.d("ResourceSync", "API client initialization took ${System.currentTimeMillis() - apiClientStartTime}ms")

            var totalRows = 0
            val totalRowsStartTime = System.currentTimeMillis()
            ApiClient.executeWithRetry {
                apiInterface.getJsonObject(Utilities.header, "${Utilities.getUrl()}/resources/_all_docs?limit=0").execute()
            }?.let { response ->
                response.body()?.let { body ->
                    if (body.has("total_rows")) {
                        totalRows = body.get("total_rows").asInt
                    }
                }
            }
            Log.d("ResourceSync", "Getting total rows took ${System.currentTimeMillis() - totalRowsStartTime}ms, total rows: $totalRows")

            val batchSize = 200
            var skip = 0
            var batchCount = 0

            while (skip < totalRows || (totalRows == 0 && skip == 0)) {
                val batchStartTime = System.currentTimeMillis()
                batchCount++
                Log.d("ResourceSync", "Starting batch $batchCount (skip: $skip)")

                try {
                    var response: JsonObject? = null
                    val apiCallStartTime = System.currentTimeMillis()
                    ApiClient.executeWithRetry {
                        apiInterface.getJsonObject(Utilities.header, "${Utilities.getUrl()}/resources/_all_docs?include_docs=true&limit=$batchSize&skip=$skip").execute()
                    }?.let {
                        response = it.body()
                    }
                    Log.d("ResourceSync", "API call for batch $batchCount took ${System.currentTimeMillis() - apiCallStartTime}ms")

                    if (response == null) {
                        Log.w("ResourceSync", "Null response for batch $batchCount, skipping")
                        skip += batchSize
                        continue
                    }

                    val rowsParseStartTime = System.currentTimeMillis()
                    val rows = getJsonArray("rows", response)
                    Log.d("ResourceSync", "Parsing rows for batch $batchCount took ${System.currentTimeMillis() - rowsParseStartTime}ms, rows count: ${rows.size()}")

                    if (rows.size() == 0) {
                        Log.d("ResourceSync", "No more rows, breaking loop")
                        break
                    }

                    // OPTIMIZATION: Process all documents in a single transaction
                    val processingStartTime = System.currentTimeMillis()
                    val batchDocuments = JsonArray()
                    val validDocuments = mutableListOf<Pair<JsonObject, String>>()

                    // First pass: collect valid documents
                    for (i in 0 until rows.size()) {
                        val rowObj = rows[i].asJsonObject
                        if (rowObj.has("doc")) {
                            val doc = getJsonObject("doc", rowObj)
                            val id = getString("_id", doc)

                            if (!id.startsWith("_design")) {
                                batchDocuments.add(doc)
                                validDocuments.add(Pair(doc, id))
                            }
                        }
                    }

                    Log.d("ResourceSync", "Collected ${validDocuments.size} valid documents for batch processing")

                    // OPTIMIZATION: Single transaction for entire batch
                    if (validDocuments.isNotEmpty()) {
                        val transactionStartTime = System.currentTimeMillis()
                        try {
                            realmInstance.beginTransaction()

                            val saveStartTime = System.currentTimeMillis()
                            val ids = save(batchDocuments, realmInstance)
                            Log.d("ResourceSync", "Batch save operation took ${System.currentTimeMillis() - saveStartTime}ms for ${batchDocuments.size()} documents")

                            if (ids.isNotEmpty()) {
                                newIds.addAll(ids)
                                processedItems += ids.size
                            }

                            if (realmInstance.isInTransaction) {
                                realmInstance.commitTransaction()
                            }
                            Log.d("ResourceSync", "Batch transaction took ${System.currentTimeMillis() - transactionStartTime}ms for ${validDocuments.size} documents")
                        } catch (e: Exception) {
                            Log.e("ResourceSync", "Error in batch processing: ${e.message}", e)
                            if (realmInstance.isInTransaction) {
                                realmInstance.cancelTransaction()
                            }

                            // Fallback: process individually
                            Log.w("ResourceSync", "Falling back to individual processing for ${validDocuments.size} documents")
                            for ((doc, id) in validDocuments) {
                                try {
                                    realmInstance.beginTransaction()
                                    val singleDocArray = JsonArray()
                                    singleDocArray.add(doc)
                                    val singleIds = save(singleDocArray, realmInstance)
                                    if (singleIds.isNotEmpty()) {
                                        newIds.addAll(singleIds)
                                        processedItems++
                                    }
                                    if (realmInstance.isInTransaction) {
                                        realmInstance.commitTransaction()
                                    }
                                } catch (e2: Exception) {
                                    Log.e("ResourceSync", "Error processing individual doc $id: ${e2.message}", e2)
                                    if (realmInstance.isInTransaction) {
                                        realmInstance.cancelTransaction()
                                    }
                                }
                            }
                        }
                    }

                    Log.d("ResourceSync", "Processing ${rows.size()} rows in batch $batchCount took ${System.currentTimeMillis() - processingStartTime}ms")

                    skip += rows.size()

                    val prefsStartTime = System.currentTimeMillis()
                    val settings = MainApplication.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    settings.edit {
                        putLong("ResourceLastSyncTime", System.currentTimeMillis())
                        putInt("ResourceSyncPosition", skip)
                    }
                    Log.v("ResourceSync", "Updating preferences took ${System.currentTimeMillis() - prefsStartTime}ms")

                    Log.d("ResourceSync", "Batch $batchCount completed in ${System.currentTimeMillis() - batchStartTime}ms, processed items so far: $processedItems")

                } catch (e: Exception) {
                    Log.e("ResourceSync", "Error in batch $batchCount: ${e.message}", e)
                    skip += batchSize
                }
            }

            val cleanupStartTime = System.currentTimeMillis()
            Log.d("ResourceSync", "Starting cleanup with ${newIds.size} new IDs")
            Log.d("ResourceSync", "Realm transaction state before cleanup: isInTransaction = ${realmInstance.isInTransaction}")

            try {
                val removeStartTime = System.currentTimeMillis()
                removeDeletedResource(newIds, realmInstance)
                Log.d("ResourceSync", "removeDeletedResource operation took ${System.currentTimeMillis() - removeStartTime}ms")
            } catch (e: Exception) {
                Log.e("ResourceSync", "Error during cleanup: ${e.message}", e)
            }
            Log.d("ResourceSync", "Total cleanup took ${System.currentTimeMillis() - cleanupStartTime}ms")

            val totalTime = System.currentTimeMillis() - functionStartTime
            Log.d("ResourceSync", "Resource sync completed in ${totalTime}ms, processed $processedItems items, average ${if (processedItems > 0) totalTime / processedItems else 0}ms per item")
            logger.endProcess("resource_sync", processedItems)
        } catch (e: Exception) {
            Log.e("ResourceSync", "Fatal error in resource sync: ${e.message}", e)
            logger.endProcess("resource_sync", processedItems)
        }
    }

    private fun fastResourceTransactionSync() {
        val logger = SyncTimeLogger.getInstance()
        logger.startProcess("resource_sync")
        var processedItems = 0

        try {
            val apiInterface = ApiClient.getEnhancedClient()
            val newIds = ConcurrentHashMap.newKeySet<String>()

            var totalRows = 0
            ApiClient.executeWithRetry {
                apiInterface.getJsonObject(Utilities.header, "${Utilities.getUrl()}/resources/_all_docs?limit=0").execute()
            }?.let { response ->
                response.body()?.let { body ->
                    if (body.has("total_rows")) {
                        totalRows = body.get("total_rows").asInt
                    }
                }
            }

            val batchSize = 1000
            val numBatches = (totalRows + batchSize - 1) / batchSize

            runBlocking {
                val semaphore = Semaphore(3)
                val batches = (0 until numBatches).map { batchIndex ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            processBatchOptimized(
                                batchIndex * batchSize,
                                batchSize,
                                apiInterface,
                                newIds
                            )
                        }
                    }
                }

                processedItems = batches.awaitAll().sum()
            }

            safeRealmOperation { realmInstance ->
                realmInstance.executeTransaction { realm ->
                    removeDeletedResource(newIds.toList(), realm)
                }
            }

            logger.endProcess("resource_sync", processedItems)
        } catch (e: Exception) {
            e.printStackTrace()
            logger.endProcess("resource_sync", processedItems)
        }
    }

    private suspend fun processBatchOptimized(skip: Int, batchSize: Int, apiInterface: ApiInterface, newIds: MutableSet<String>): Int {
        var processedCount = 0

        try {
            var response: JsonObject? = null
            ApiClient.executeWithRetry {
                apiInterface.getJsonObject(Utilities.header, "${Utilities.getUrl()}/resources/_all_docs?include_docs=true&limit=$batchSize&skip=$skip").execute()
            }?.let {
                response = it.body()
            }

            if (response == null) return 0

            val rows = getJsonArray("rows", response)
            if (rows.size() == 0) return 0

            val validDocs = mutableListOf<JsonObject>()
            val batchIds = mutableListOf<String>()

            for (i in 0 until rows.size()) {
                val rowObj = rows[i].asJsonObject
                if (rowObj.has("doc")) {
                    val doc = getJsonObject("doc", rowObj)
                    val id = getString("_id", doc)

                    if (!id.startsWith("_design")) {
                        validDocs.add(doc)
                        batchIds.add(id)
                    }
                }
            }

            if (validDocs.isEmpty()) return 0

            safeRealmOperation { realmInstance ->
                realmInstance.executeTransaction { realm ->
                    val bulkArray = JsonArray()
                    validDocs.forEach { doc -> bulkArray.add(doc) }

                    try {
                        val savedIds = save(bulkArray, realm)
                        newIds.addAll(savedIds)
                        processedCount = savedIds.size
                    } catch (e: Exception) {
                        e.printStackTrace()
                        validDocs.forEach { doc ->
                            try {
                                val singleDocArray = JsonArray()
                                singleDocArray.add(doc)
                                val ids = save(singleDocArray, realm)
                                if (ids.isNotEmpty()) {
                                    newIds.addAll(ids)
                                    processedCount++
                                }
                            } catch (individualE: Exception) {
                                individualE.printStackTrace()
                            }
                        }
                    }
                }
            }

            if (skip % (batchSize * 10) == 0) {
                val settings = MainApplication.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                settings.edit {
                    putLong("ResourceLastSyncTime", System.currentTimeMillis())
                    putInt("ResourceSyncPosition", skip + rows.size())
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return processedCount
    }

    private fun myLibraryTransactionSync(backgroundRealm: Realm? = null) {
        val logger = SyncTimeLogger.getInstance()
        logger.startProcess("library_sync")
        var processedItems = 0
        val functionStartTime = System.currentTimeMillis()
        Log.d("LibrarySync", "Starting library sync at ${Date(functionStartTime)}")

        try {
            val apiClientStartTime = System.currentTimeMillis()
            val apiInterface = ApiClient.getEnhancedClient()
            val realmInstance = backgroundRealm ?: mRealm
            Log.d("LibrarySync", "API client initialization took ${System.currentTimeMillis() - apiClientStartTime}ms")

            var shelfResponse: DocumentResponse? = null
            val shelfResponseStartTime = System.currentTimeMillis()
            ApiClient.executeWithRetry {
                apiInterface.getDocuments(Utilities.header, "${Utilities.getUrl()}/shelf/_all_docs?include_docs=true").execute()
            }?.let {
                shelfResponse = it.body()
            }
            Log.d("LibrarySync", "Getting shelf response took ${System.currentTimeMillis() - shelfResponseStartTime}ms")

            if (shelfResponse?.rows == null || shelfResponse.rows?.isEmpty() == true) {
                Log.d("LibrarySync", "No shelf rows found, returning early")
                return
            }

            val rows = shelfResponse.rows!!
            Log.d("LibrarySync", "Processing ${rows.size} shelves")

            for ((shelfIndex, row) in rows.withIndex()) {
                val shelfStartTime = System.currentTimeMillis()
                val shelfId = row.id
                Log.d("LibrarySync", "Processing shelf ${shelfIndex + 1}/${rows.size}: $shelfId")

                var shelfDoc: JsonObject? = null
                val shelfDocStartTime = System.currentTimeMillis()
                ApiClient.executeWithRetry {
                    apiInterface.getJsonObject(Utilities.header, "${Utilities.getUrl()}/shelf/$shelfId").execute()
                }?.let {
                    shelfDoc = it.body()
                }
                Log.d("LibrarySync", "Getting shelf doc for $shelfId took ${System.currentTimeMillis() - shelfDocStartTime}ms")

                if (shelfDoc == null) {
                    Log.w("LibrarySync", "Null shelf doc for $shelfId, continuing")
                    continue
                }

                // OPTIMIZATION: Quick check if shelf has any data before processing
                var shelfHasData = false
                val preCheckStartTime = System.currentTimeMillis()
                for (shelfData in Constants.shelfDataList) {
                    val array = getJsonArray(shelfData.key, shelfDoc)
                    if (array.size() > 0) {
                        shelfHasData = true
                        break
                    }
                }
                Log.v("LibrarySync", "Pre-check for shelf $shelfId took ${System.currentTimeMillis() - preCheckStartTime}ms, has data: $shelfHasData")

                if (!shelfHasData) {
                    Log.d("LibrarySync", "Shelf $shelfId has no data, skipping")
                    continue
                }

                for ((dataIndex, shelfData) in Constants.shelfDataList.withIndex()) {
                    val shelfDataStartTime = System.currentTimeMillis()
                    Log.d("LibrarySync", "Processing shelf data ${dataIndex + 1}/${Constants.shelfDataList.size} for shelf $shelfId: ${shelfData.key}")

                    val arrayParseStartTime = System.currentTimeMillis()
                    val array = getJsonArray(shelfData.key, shelfDoc)
                    Log.v("LibrarySync", "Parsing array for ${shelfData.key} took ${System.currentTimeMillis() - arrayParseStartTime}ms, size: ${array.size()}")

                    if (array.size() == 0) {
                        Log.v("LibrarySync", "Empty array for ${shelfData.key}, continuing")
                        continue
                    }

                    stringArray[0] = shelfId
                    stringArray[1] = shelfData.categoryKey
                    stringArray[2] = shelfData.type

                    val validationStartTime = System.currentTimeMillis()
                    val validIds = mutableListOf<String>()
                    for (i in 0 until array.size()) {
                        if (array[i] !is JsonNull) {
                            validIds.add(array[i].asString)
                        }
                    }
                    Log.v("LibrarySync", "Validating IDs for ${shelfData.key} took ${System.currentTimeMillis() - validationStartTime}ms, valid count: ${validIds.size}")

                    if (validIds.isEmpty()) {
                        Log.v("LibrarySync", "No valid IDs for ${shelfData.key}, continuing")
                        continue
                    }

                    val batchSize = 50
                    val totalBatches = (validIds.size + batchSize - 1) / batchSize
                    Log.d("LibrarySync", "Processing ${validIds.size} items in $totalBatches batches for ${shelfData.key}")

                    for (i in 0 until validIds.size step batchSize) {
                        val batchStartTime = System.currentTimeMillis()
                        val batchNumber = (i / batchSize) + 1
                        Log.d("LibrarySync", "Processing batch $batchNumber/$totalBatches for ${shelfData.key}")

                        val end = minOf(i + batchSize, validIds.size)
                        val batch = validIds.subList(i, end)

                        try {
                            val requestStartTime = System.currentTimeMillis()
                            val keysObject = JsonObject()
                            keysObject.add("keys", Gson().fromJson(Gson().toJson(batch), JsonArray::class.java))

                            var response: JsonObject? = null
                            ApiClient.executeWithRetry {
                                apiInterface.findDocs(Utilities.header, "application/json", "${Utilities.getUrl()}/${shelfData.type}/_all_docs?include_docs=true", keysObject).execute()
                            }?.let {
                                response = it.body()
                            }
                            Log.d("LibrarySync", "API request for batch $batchNumber took ${System.currentTimeMillis() - requestStartTime}ms")

                            if (response == null) {
                                Log.w("LibrarySync", "Null response for batch $batchNumber, continuing")
                                continue
                            }

                            val rowsParseStartTime = System.currentTimeMillis()
                            val batchRows = getJsonArray("rows", response)
                            Log.d("LibrarySync", "Parsing rows for batch $batchNumber took ${System.currentTimeMillis() - rowsParseStartTime}ms, rows: ${batchRows.size()}")

                            // OPTIMIZATION: Process all documents in batch in a single transaction
                            val processingStartTime = System.currentTimeMillis()
                            val documentsToProcess = mutableListOf<Pair<JsonObject, String>>()

                            for (j in 0 until batchRows.size()) {
                                val rowObj = batchRows[j].asJsonObject
                                if (rowObj.has("doc")) {
                                    val doc = getJsonObject("doc", rowObj)
                                    val docId = getString("_id", doc)
                                    documentsToProcess.add(Pair(doc, docId))
                                }
                            }

                            if (documentsToProcess.isNotEmpty()) {
                                val batchTransactionStartTime = System.currentTimeMillis()
                                try {
                                    realmInstance.beginTransaction()

                                    for ((doc, docId) in documentsToProcess) {
                                        when (shelfData.type) {
                                            "resources" -> insertMyLibrary(shelfId, doc, realmInstance)
                                            "meetups" -> insert(realmInstance, doc)
                                            "courses" -> insertMyCourses(shelfId, doc, realmInstance)
                                            "teams" -> insertMyTeams(doc, realmInstance)
                                        }
                                    }

                                    if (realmInstance.isInTransaction) {
                                        realmInstance.commitTransaction()
                                        processedItems += documentsToProcess.size
                                    }
                                    Log.d("LibrarySync", "Batch transaction for ${documentsToProcess.size} docs took ${System.currentTimeMillis() - batchTransactionStartTime}ms")
                                } catch (e: Exception) {
                                    Log.e("LibrarySync", "Error in batch transaction: ${e.message}", e)
                                    if (realmInstance.isInTransaction) {
                                        realmInstance.cancelTransaction()
                                    }

                                    // Fallback: process individually
                                    Log.w("LibrarySync", "Falling back to individual processing for ${documentsToProcess.size} documents")
                                    for ((doc, docId) in documentsToProcess) {
                                        try {
                                            realmInstance.beginTransaction()
                                            when (shelfData.type) {
                                                "resources" -> insertMyLibrary(shelfId, doc, realmInstance)
                                                "meetups" -> insert(realmInstance, doc)
                                                "courses" -> insertMyCourses(shelfId, doc, realmInstance)
                                                "teams" -> insertMyTeams(doc, realmInstance)
                                            }
                                            if (realmInstance.isInTransaction) {
                                                realmInstance.commitTransaction()
                                                processedItems++
                                            }
                                        } catch (e2: Exception) {
                                            Log.e("LibrarySync", "Error processing individual doc $docId: ${e2.message}", e2)
                                            if (realmInstance.isInTransaction) {
                                                realmInstance.cancelTransaction()
                                            }
                                        }
                                    }
                                }
                            }

                            Log.d("LibrarySync", "Processing ${batchRows.size()} docs in batch $batchNumber took ${System.currentTimeMillis() - processingStartTime}ms")
                            Log.d("LibrarySync", "Batch $batchNumber completed in ${System.currentTimeMillis() - batchStartTime}ms")

                        } catch (e: Exception) {
                            Log.e("LibrarySync", "Error in batch $batchNumber for ${shelfData.key}: ${e.message}", e)
                        }
                    }
                    Log.d("LibrarySync", "Completed shelf data ${shelfData.key} in ${System.currentTimeMillis() - shelfDataStartTime}ms")
                }
                Log.d("LibrarySync", "Completed shelf $shelfId in ${System.currentTimeMillis() - shelfStartTime}ms")
            }

            val prefsStartTime = System.currentTimeMillis()
            saveConcatenatedLinksToPrefs()
            Log.d("LibrarySync", "Saving concatenated links took ${System.currentTimeMillis() - prefsStartTime}ms")

            val totalTime = System.currentTimeMillis() - functionStartTime
            Log.d("LibrarySync", "Library sync completed in ${totalTime}ms, processed $processedItems items, average ${if (processedItems > 0) totalTime / processedItems else 0}ms per item")
            logger.endProcess("library_sync", processedItems)
        } catch (e: Exception) {
            Log.e("LibrarySync", "Fatal error in library sync: ${e.message}", e)
            logger.endProcess("library_sync", processedItems)
        }
    }

    private fun fastMyLibraryTransactionSync() {
        val logger = SyncTimeLogger.getInstance()
        logger.startProcess("library_sync")
        var processedItems = 0

        try {
            val apiInterface = ApiClient.getEnhancedClient()

            var shelfResponse: DocumentResponse? = null
            ApiClient.executeWithRetry {
                apiInterface.getDocuments(Utilities.header, "${Utilities.getUrl()}/shelf/_all_docs?include_docs=true").execute()
            }?.let {
                shelfResponse = it.body()
            }

            val rows = shelfResponse?.rows
            if (rows == null || rows.isEmpty()) {
                return
            }

            runBlocking {
                val semaphore = Semaphore(4)
                val shelfJobs = rows.map { row ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            processShelfUltraOptimized(row, apiInterface)
                        }
                    }
                }

                processedItems = shelfJobs.awaitAll().sum()
            }

            saveConcatenatedLinksToPrefs()
            logger.endProcess("library_sync", processedItems)

        } catch (e: Exception) {
            e.printStackTrace()
            logger.endProcess("library_sync", processedItems)
        }
    }

    private suspend fun processShelfUltraOptimized(row: Rows, apiInterface: ApiInterface): Int {
        var processedItems = 0
        val shelfId = row.id

        try {
            var shelfDoc: JsonObject? = null
            ApiClient.executeWithRetry {
                apiInterface.getJsonObject(Utilities.header, "${Utilities.getUrl()}/shelf/$shelfId").execute()
            }?.let {
                shelfDoc = it.body()
            }

            if (shelfDoc == null) return 0
            coroutineScope {
                val shelfDataJobs = Constants.shelfDataList.map { shelfData ->
                    async(Dispatchers.IO) {
                        try {
                            ensureActive()
                            processShelfDataOptimized(shelfId, shelfData, shelfDoc, apiInterface)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            e.printStackTrace()
                            0
                        }
                    }
                }

                processedItems = shelfDataJobs.awaitAll().sum()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return processedItems
    }

    private suspend fun processShelfDataOptimized(shelfId: String?, shelfData: Constants.ShelfData, shelfDoc: JsonObject, apiInterface: ApiInterface): Int {
        var processedCount = 0

        try {
            val array = getJsonArray(shelfData.key, shelfDoc)
            if (array.size() == 0) return 0

            stringArray[0] = shelfId
            stringArray[1] = shelfData.categoryKey
            stringArray[2] = shelfData.type

            val validIds = mutableListOf<String>()
            for (i in 0 until array.size()) {
                if (array[i] !is JsonNull) {
                    validIds.add(array[i].asString)
                }
            }

            if (validIds.isEmpty()) return 0

            val batchSize = 500
            val results = validIds.chunked(batchSize).map { batch ->
                withContext(Dispatchers.IO) {
                    safeRealmOperation { threadRealm ->
                        processBatchForShelfData(batch, shelfData, shelfId, apiInterface, threadRealm)
                    } ?: 0
                }
            }

            processedCount = results.sum()

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return processedCount
    }

    private fun <T> safeRealmOperation(operation: (Realm) -> T): T? {
        var realm: Realm? = null
        return try {
            realm = Realm.getDefaultInstance()
            operation(realm)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            realm?.let { r ->
                try {
                    if (!r.isClosed) {
                        r.close()
                    }
                } catch (e: IllegalStateException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun processBatchForShelfData(batch: List<String>, shelfData: Constants.ShelfData, shelfId: String?, apiInterface: ApiInterface, realmInstance: Realm): Int {
        var processedCount = 0

        try {
            val keysObject = JsonObject()
            keysObject.add("keys", Gson().fromJson(Gson().toJson(batch), JsonArray::class.java))

            var response: JsonObject? = null
            ApiClient.executeWithRetry {
                apiInterface.findDocs(Utilities.header, "application/json", "${Utilities.getUrl()}/${shelfData.type}/_all_docs?include_docs=true", keysObject).execute()
            }?.let {
                response = it.body()
            }

            if (response == null) return 0

            val responseRows = getJsonArray("rows", response)
            if (responseRows.size() == 0) return 0

            val documentsToProcess = mutableListOf<JsonObject>()
            for (j in 0 until responseRows.size()) {
                val rowObj = responseRows[j].asJsonObject
                if (rowObj.has("doc")) {
                    val doc = getJsonObject("doc", rowObj)
                    documentsToProcess.add(doc)
                }
            }

            if (documentsToProcess.isNotEmpty()) {
                realmInstance.executeTransaction { realm ->
                    documentsToProcess.forEach { doc ->
                        try {
                            when (shelfData.type) {
                                "resources" -> insertMyLibrary(shelfId, doc, realm)
                                "meetups" -> insert(realm, doc)
                                "courses" -> insertMyCourses(shelfId, doc, realm)
                                "teams" -> insertMyTeams(doc, realm)
                            }
                            processedCount++
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return processedCount
    }

    fun cleanup() {
        syncScope.cancel()
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

