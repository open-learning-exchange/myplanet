package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
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
import kotlin.system.measureTimeMillis
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
    val _syncState = MutableLiveData<Boolean>()
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val semaphore = Semaphore(5)

    fun start(listener: SyncListener?, type: String) {
        this.listener = listener
        if (!isSyncing) {
            settings.edit { remove("concatenated_links") }
            listener?.onSyncStarted()
            authenticateAndSync(type)
        }
    }

    private fun destroy() {
        cleanup()
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
                // Phase 1: Critical syncs (sequential)
                async { syncWithSemaphore("tablet_users") { TransactionSyncManager.syncDb(mRealm, "tablet_users") }}.await()

                // Phase 2: Major syncs in parallel (this is the key optimization)
                val majorSyncs = listOf(
                    async(Dispatchers.IO) { resourceTransactionSync() },
                    async(Dispatchers.IO) { myLibraryTransactionSync() }
                )
                majorSyncs.awaitAll()

                // Phase 3: Remaining syncs in parallel
                val remainingSyncs = listOf(
                    async { syncWithSemaphore("courses") { TransactionSyncManager.syncDb(mRealm, "courses") }},
                    async { syncWithSemaphore("exams") { TransactionSyncManager.syncDb(mRealm, "exams") }},
                    async { syncWithSemaphore("ratings") { TransactionSyncManager.syncDb(mRealm, "ratings") }},
                    async { syncWithSemaphore("achievements") { TransactionSyncManager.syncDb(mRealm, "achievements") }},
                    async { syncWithSemaphore("tags") { TransactionSyncManager.syncDb(mRealm, "tags") }},
                    async { syncWithSemaphore("news") { TransactionSyncManager.syncDb(mRealm, "news") }},
                    async { syncWithSemaphore("feedback") { TransactionSyncManager.syncDb(mRealm, "feedback") }},
                    async { syncWithSemaphore("teams") { TransactionSyncManager.syncDb(mRealm, "teams") }},
                    async { syncWithSemaphore("meetups") { TransactionSyncManager.syncDb(mRealm, "meetups") }},
                    async { syncWithSemaphore("health") { TransactionSyncManager.syncDb(mRealm, "health") }},
                    async { syncWithSemaphore("certifications") { TransactionSyncManager.syncDb(mRealm, "certifications") }},
                    async { syncWithSemaphore("courses_progress") { TransactionSyncManager.syncDb(mRealm, "courses_progress") }},
                    async { syncWithSemaphore("submissions") { TransactionSyncManager.syncDb(mRealm, "submissions") }},
                    async { syncWithSemaphore("tasks") { TransactionSyncManager.syncDb(mRealm, "tasks") }},
                    async { syncWithSemaphore("login_activities") { TransactionSyncManager.syncDb(mRealm, "login_activities") }},
                    async { syncWithSemaphore("team_activities") { TransactionSyncManager.syncDb(mRealm, "team_activities") }},
                    async { syncWithSemaphore("chat_history") { TransactionSyncManager.syncDb(mRealm, "chat_history") }}
                )
                remainingSyncs.awaitAll()
            }

            // Sequential final steps
            logger.startProcess("admin_sync")
            ManagerSync.instance?.syncAdmin()
            logger.endProcess("admin_sync")

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

        try {
            val apiInterface = ApiClient.getEnhancedClient()
            val realmInstance = backgroundRealm ?: mRealm
            val newIds = ConcurrentHashMap.newKeySet<String>()

            // Get total count with minimal data transfer
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

            // Aggressive batching - increase from 200 to 1000
            val batchSize = 1000
            val numBatches = (totalRows + batchSize - 1) / batchSize

            // Process batches in parallel with controlled concurrency
            runBlocking {
                val semaphore = Semaphore(3) // Allow 3 concurrent batch downloads
                val batches = (0 until numBatches).map { batchIndex ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            processBatchOptimized(
                                batchIndex * batchSize,
                                batchSize,
                                apiInterface,
                                realmInstance,
                                newIds
                            )
                        }
                    }
                }

                processedItems = batches.awaitAll().sum()
            }

            // Final cleanup in optimized single transaction
            realmInstance.executeTransaction { realm ->
                removeDeletedResource(newIds.toList(), realm)
            }

            logger.endProcess("resource_sync", processedItems)
        } catch (e: Exception) {
            e.printStackTrace()
            logger.endProcess("resource_sync", processedItems)
        }
    }

    private suspend fun processBatchOptimized(
        skip: Int,
        batchSize: Int,
        apiInterface: ApiInterface,
        realmInstance: Realm,
        newIds: MutableSet<String>
    ): Int {
        var processedCount = 0

        try {
            var response: JsonObject? = null
            ApiClient.executeWithRetry {
                apiInterface.getJsonObject(
                    Utilities.header,
                    "${Utilities.getUrl()}/resources/_all_docs?include_docs=true&limit=$batchSize&skip=$skip"
                ).execute()
            }?.let {
                response = it.body()
            }

            if (response == null) return 0

            val rows = getJsonArray("rows", response)
            if (rows.size() == 0) return 0

            // Pre-filter valid documents
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

            // Process entire batch in single optimized transaction
            realmInstance.executeTransaction { realm ->
                // Bulk insert using JsonArray for maximum efficiency
                val bulkArray = JsonArray()
                validDocs.forEach { doc -> bulkArray.add(doc) }

                try {
                    val savedIds = save(bulkArray, realm)
                    newIds.addAll(savedIds)
                    processedCount = savedIds.size
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Fallback to individual processing if bulk fails
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

            // Update progress less frequently - only every 10 batches
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

        try {
            val apiInterface = ApiClient.getEnhancedClient()
            val realmInstance = backgroundRealm ?: mRealm

            // Get all shelf documents in one call
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

            // Process all shelves in parallel with aggressive optimization
            runBlocking {
                val semaphore = Semaphore(4) // Allow 4 concurrent shelf processing
                val shelfJobs = rows.map { row ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            processShelfUltraOptimized(row, apiInterface, realmInstance)
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

    private suspend fun processShelfUltraOptimized(row: Rows, apiInterface: ApiInterface, realmInstance: Realm): Int {
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

            // Process all shelf data types in parallel - Each gets its own Realm instance
            coroutineScope {
                val shelfDataJobs = Constants.shelfDataList.map { shelfData ->
                    async(Dispatchers.IO) {
                        var threadRealm: Realm? = null
                        try {
                            // Check if coroutine is still active before creating Realm
                            ensureActive()
                            threadRealm = Realm.getDefaultInstance()
                            processShelfDataOptimized(shelfId, shelfData, shelfDoc, apiInterface, threadRealm)
                        } catch (e: CancellationException) {
                            // Handle cancellation gracefully
                            throw e
                        } catch (e: Exception) {
                            e.printStackTrace()
                            0
                        } finally {
                            // Only close if we're still on the same thread and not cancelled
                            threadRealm?.let { realm ->
                                try {
                                    if (!realm.isClosed && isActive) {
                                        realm.close()
                                    }
                                } catch (e: IllegalStateException) {
                                    // Realm was created on different thread or context is cancelled
                                    // Log but don't crash - Realm will be garbage collected
                                    Log.w("SyncManager", "Could not close Realm on same thread: ${e.message}")
                                }
                            }
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

    private suspend fun processShelfDataOptimized(
        shelfId: String?,
        shelfData: Constants.ShelfData,
        shelfDoc: JsonObject,
        apiInterface: ApiInterface,
        realmInstance: Realm
    ): Int {
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

            // Use withContext instead of async with manual Realm management
            val results = validIds.chunked(batchSize).map { batch ->
                withContext(Dispatchers.IO) {
                    val threadRealm = Realm.getDefaultInstance()
                    try {
                        processBatchForShelfData(batch, shelfData, shelfId, apiInterface, threadRealm)
                    } finally {
                        if (!threadRealm.isClosed) {
                            threadRealm.close()
                        }
                    }
                }
            }

            processedCount = results.sum()

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return processedCount
    }

    // Additional utility function for safer Realm operations
    private inline fun <T> safeRealmOperation(crossinline operation: (Realm) -> T): T? {
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
                    Log.w("SyncManager", "Could not close Realm safely: ${e.message}")
                }
            }
        }
    }

    private suspend fun processBatchForShelfData(
        batch: List<String>,
        shelfData: Constants.ShelfData,
        shelfId: String?,
        apiInterface: ApiInterface,
        realmInstance: Realm
    ): Int {
        var processedCount = 0

        try {
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

            if (response == null) return 0

            val responseRows = getJsonArray("rows", response)
            if (responseRows.size() == 0) return 0

            // Extract and pre-process all documents
            val documentsToProcess = mutableListOf<JsonObject>()
            for (j in 0 until responseRows.size()) {
                val rowObj = responseRows[j].asJsonObject
                if (rowObj.has("doc")) {
                    val doc = getJsonObject("doc", rowObj)
                    documentsToProcess.add(doc)
                }
            }

            // Process entire batch in single mega-transaction
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
                            // Continue with other documents
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
