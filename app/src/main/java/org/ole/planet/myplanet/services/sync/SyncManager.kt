package org.ole.planet.myplanet.services.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.SupplicantState
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.data.api.ApiClient
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.model.MyCourse.Companion.saveConcatenatedLinksToPrefs
import org.ole.planet.myplanet.model.Rows
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.SyncRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.UserSyncRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils.getInt
import org.ole.planet.myplanet.utils.JsonUtils.getJsonArray
import org.ole.planet.myplanet.utils.JsonUtils.getJsonObject
import org.ole.planet.myplanet.utils.JsonUtils.getString
import org.ole.planet.myplanet.utils.NotificationUtils.cancel
import org.ole.planet.myplanet.utils.NotificationUtils.create
import org.ole.planet.myplanet.utils.SyncTimeLogger
import org.ole.planet.myplanet.utils.TimeProvider
import org.ole.planet.myplanet.utils.UrlUtils

@Singleton
class SyncManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sharedPrefManager: SharedPrefManager,
    private val apiInterface: ApiInterface,
    private val transactionSyncManager: TransactionSyncManager,
    private val resourcesRepository: ResourcesRepository,
    private val loginSyncManager: LoginSyncManager,
    @param:ApplicationScope private val syncScope: CoroutineScope,
    private val activitiesRepository: ActivitiesRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val timeProvider: TimeProvider,
    private val userSyncRepository: UserSyncRepository,
    private val userRepository: UserRepository,
    private val syncRepository: SyncRepository,
    private val syncTimeLogger: SyncTimeLogger
) {
    private val timestampFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    private val isSyncing = AtomicBoolean(false)
    private var listener: OnSyncListener? = null
    private var backgroundSync: Job? = null
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus

    fun start(listener: OnSyncListener?, type: String, syncTables: List<String>? = null) {
        this.listener = listener
        if (isSyncing.compareAndSet(false, true)) {
            _syncStatus.value = SyncStatus.Idle
            sharedPrefManager.removeKey("concatenated_links")
            listener?.onSyncStarted()
            _syncStatus.value = SyncStatus.Syncing()
            authenticateAndSync()
        }
    }

    sealed class SyncStatus {
        object Idle : SyncStatus()
        data class Syncing(
            val phase: String = "",
            val phaseIndex: Int = 0,
            val totalPhases: Int = 4,
            val itemsDone: Int = 0,
            val itemsTotal: Int = 0,
            val countLabel: String = ""
        ) : SyncStatus()
        data class Success(val message: String) : SyncStatus()
        data class Error(val message: String) : SyncStatus()
    }

    fun resetSyncStatus() {
        _syncStatus.value = SyncStatus.Idle
    }

    fun isMainSyncActive(): Boolean = isSyncing.get()

    private fun destroy() {
        cancelBackgroundSync()
        cancel(context, 111)
        isSyncing.set(false)
        sharedPrefManager.setLastSync(Date().time)
        listener?.onSyncComplete()
        listener = null
        _syncStatus.value = SyncStatus.Success("Sync completed")
    }

    private fun authenticateAndSync() {
        backgroundSync = syncScope.launch(dispatcherProvider.io) {
            if (transactionSyncManager.authenticate()) {
                startFullSync()
            } else {
                handleException(context.getString(R.string.invalid_configuration))
                cleanupMainSync()
            }
        }
    }

    private suspend fun startFullSync() {
        val syncStartTime = SystemClock.elapsedRealtime()

        syncTimeLogger.startLogging()
        Log.d("SyncPerf", "═══════════════════════════════════════════════════════════════")
        Log.d("SyncPerf", "FULL SYNC STARTED at ${timestampFormat.format(Instant.now())}")
        Log.d("SyncPerf", "═══════════════════════════════════════════════════════════════")
        try {

            initializeSync()

            syncTimeLogger.startProcess("shelf_push")
            pushCurrentUserShelf()
            syncTimeLogger.endProcess("shelf_push")

            val parallelTables = listOf(
                "tablet_users", "exams", "achievements",
                "tags", "news", "feedback", "tasks",
                "health", "certifications", "chat_history", "teams",
                "meetups", "courses", "notifications"
            )
            val completedTables = AtomicInteger(0)
            _syncStatus.value = SyncStatus.Syncing(context.getString(R.string.sync_phase_account_data), 1, 4,
                0, parallelTables.size)
            coroutineScope {
                val syncJobs = parallelTables.map { tableName ->
                    async {
                        syncTimeLogger.startProcess("${tableName}_sync")
                        transactionSyncManager.syncDb(tableName)
                        syncTimeLogger.endProcess("${tableName}_sync")
                        val done = completedTables.incrementAndGet()
                        _syncStatus.value = SyncStatus.Syncing(
                            context.getString(R.string.sync_phase_account_data), 1, 4,
                            done, parallelTables.size,
                            context.getString(R.string.sync_table_progress, tableName, done, parallelTables.size)
                        )
                    }
                }
                syncJobs.awaitAll()
            }

            _syncStatus.value = SyncStatus.Syncing(context.getString(R.string.sync_phase_resources), 2, 4)
            syncTimeLogger.startProcess("resource_sync")
            resourceTransactionSync()
            syncTimeLogger.endProcess("resource_sync")

            _syncStatus.value = SyncStatus.Syncing(context.getString(R.string.sync_phase_library), 3, 4)
            syncTimeLogger.startProcess("library_sync")
            myLibraryTransactionSync()
            syncTimeLogger.endProcess("library_sync")

            _syncStatus.value = SyncStatus.Syncing(context.getString(R.string.sync_phase_finalizing), 4, 4)
            syncTimeLogger.startProcess("admin_sync")
            loginSyncManager.syncAdmin()
            syncTimeLogger.endProcess("admin_sync")

            syncTimeLogger.startProcess("notification_reads_upload")
            transactionSyncManager.syncNotificationReads()
            syncTimeLogger.endProcess("notification_reads_upload")

            syncTimeLogger.startProcess("on_synced")
            activitiesRepository.recordSyncActivity(sharedPrefManager.rawPreferences.getString("userId", "") ?: "")
            syncTimeLogger.endProcess("on_synced")

            syncTimeLogger.stopLogging()

            HeavyTableSyncWorker.schedule(context)

            val syncEndTime = SystemClock.elapsedRealtime()
            val totalSyncTime = syncEndTime - syncStartTime
            val minutes = totalSyncTime / 60000
            val seconds = (totalSyncTime % 60000) / 1000
            Log.d("SyncPerf", "═══════════════════════════════════════════════════════════════")
            Log.d("SyncPerf", "FULL SYNC COMPLETED at ${timestampFormat.format(Instant.now())}")
            Log.d("SyncPerf", "TOTAL SYNC TIME: ${minutes}m ${seconds}s (${totalSyncTime}ms)")
            Log.d("SyncPerf", "═══════════════════════════════════════════════════════════════")
        } catch (err: Exception) {
            val syncEndTime = SystemClock.elapsedRealtime()
            val totalSyncTime = syncEndTime - syncStartTime
            Log.d("SyncPerf", "═══════════════════════════════════════════════════════════════")
            Log.d("SyncPerf", "SYNC FAILED after ${totalSyncTime}ms")
            Log.d("SyncPerf", "Error: ${err.message}")
            Log.d("SyncPerf", "═══════════════════════════════════════════════════════════════")
            Log.e("SyncManager", "Full sync failed", err)
            handleException(err.message)
        } finally {
            destroy()
        }
    }

    private fun cleanupMainSync() {
        cancel(context, 111)
        isSyncing.set(false)
    }

    private fun initializeSync() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            var ssid: String? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val wifiInfo = capabilities.transportInfo as? WifiInfo
                ssid = wifiInfo?.ssid
            } else {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val wifiInfo = wifiManager.connectionInfo
                if (wifiInfo.supplicantState == SupplicantState.COMPLETED) {
                    ssid = wifiInfo.ssid
                }
            }

            if (ssid != null) {
                sharedPrefManager.setLastWifiSsid(ssid)
            }
        }
        create(context, R.mipmap.ic_launcher, "Syncing data", "Please wait...")
    }

    private suspend fun pushCurrentUserShelf() {
        try {
            userRepository.getUserModel()?.let { userSyncRepository.uploadShelfData(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("SyncManager", "Failed to push shelf data before sync", e)
        }
    }

    fun cancelBackgroundSync() {
        backgroundSync?.cancel()
        backgroundSync = null
        listener = null
    }

    private suspend fun resourceTransactionSync() {
        val resourceSyncStartTime = SystemClock.elapsedRealtime()
        Log.d("SyncPerf", "  ▶ Starting resource sync")


        syncTimeLogger.startProcess("resource_sync_main")
        var processedItems = 0

        try {
            val url = UrlUtils.getUrl()
            val header = UrlUtils.header

            val newIds: MutableList<String?> = ArrayList()
            var totalRows = 0
            var hadBatchFailure = false

            syncTimeLogger.startProcess("resource_get_total_count")
            val countApiStartTime = SystemClock.elapsedRealtime()
            ApiClient.executeWithRetryAndWrap {
                apiInterface.getJsonObject(header, "$url/resources/_all_docs?limit=0")
            }?.let { response ->
                response.body()?.let { body ->
                    totalRows = getInt("total_rows", body)
                }
            }
            val countApiDuration = SystemClock.elapsedRealtime() - countApiStartTime
            syncTimeLogger.logApiCall("$url/resources/_all_docs?limit=0", countApiDuration, true, totalRows)
            syncTimeLogger.endProcess("resource_get_total_count")

            val batchSizer = AdaptiveBatchProcessor(initialSize = 100)
            var skip = 0
            var batchCount = 0

            Log.d("SyncPerf", "    Resources: Found $totalRows documents to sync")
            syncTimeLogger.logDetail("resource_sync", "Total resources: $totalRows, batch size: ${batchSizer.currentSize} (adaptive)")

            while (skip < totalRows || (totalRows == 0 && skip == 0)) {
                batchCount++
                val batchSize = batchSizer.currentSize
                val batchStartTime = SystemClock.elapsedRealtime()

                try {
                    val batchApiStartTime = SystemClock.elapsedRealtime()
                    var response: JsonObject? = null
                    ApiClient.executeWithRetryAndWrap {
                        apiInterface.getJsonObject(header, "$url/resources/_all_docs?include_docs=true&limit=$batchSize&skip=$skip")
                    }?.let {
                        response = it.body()
                    }
                    val batchApiDuration = SystemClock.elapsedRealtime() - batchApiStartTime

                    if (response == null) {
                        batchSizer.recordFailure()
                        hadBatchFailure = true
                        syncTimeLogger.logApiCall("$url/resources/_all_docs (batch $batchCount)", batchApiDuration, false, 0)
                        skip += batchSize
                        continue
                    }
                    batchSizer.recordSuccess(batchApiDuration)

                    val rows = getJsonArray("rows", response)
                    syncTimeLogger.logApiCall("$url/resources/_all_docs (batch $batchCount)", batchApiDuration, true, rows.size())

                    if (rows.isEmpty()) {
                        break
                    }

                    val parseStartTime = SystemClock.elapsedRealtime()
                    val validDocuments = mutableListOf<JsonObject>()

                    for (rowElement in rows) {
                        val rowObj = rowElement.asJsonObject
                        if (rowObj.has("doc")) {
                            val doc = getJsonObject("doc", rowObj)
                            val id = getString("_id", doc)

                            if (!id.startsWith("_design") && id.isNotBlank()) {
                                validDocuments.add(doc)
                            }
                        }
                    }
                    val parseDuration = SystemClock.elapsedRealtime() - parseStartTime
                    if (parseDuration > 100) {
                        syncTimeLogger.logDetail("resource_sync", "Batch $batchCount: Parse took ${parseDuration}ms for ${rows.size()} docs")
                    }

                    if (validDocuments.isNotEmpty()) {
                        val realmInsertStartTime = SystemClock.elapsedRealtime()
                        val savedIds = resourcesRepository.batchInsertResources(validDocuments)
                        val realmInsertDuration = SystemClock.elapsedRealtime() - realmInsertStartTime
                        syncTimeLogger.logDbOperation("insert_chunks", "resources", realmInsertDuration, validDocuments.size)

                        if (savedIds.isNotEmpty()) {
                            val validIds = savedIds.filter { it.isNotBlank() }
                            newIds.addAll(validIds)
                            processedItems += validIds.size
                        }
                    }

                    skip += rows.size()
                    val resourcesDone = skip.coerceAtMost(totalRows)
                    _syncStatus.value = SyncStatus.Syncing(
                        context.getString(R.string.sync_phase_resources), 2, 4,
                        resourcesDone, totalRows,
                        context.getString(R.string.sync_items_of, resourcesDone, totalRows)
                    )

                    val batchEndTime = SystemClock.elapsedRealtime()
                    val batchTime = batchEndTime - batchStartTime
                    if (batchCount % 10 == 0) {
                        Log.d("SyncPerf", "    Resources batch $batchCount: ${batchTime}ms - Progress: $skip/$totalRows (${(skip * 100 / totalRows.coerceAtLeast(1))}%)")
                        syncTimeLogger.logDetail("resource_sync", "Batch $batchCount progress: $skip/$totalRows (${(skip * 100 / totalRows.coerceAtLeast(1))}%)")
                        sharedPrefManager.rawPreferences.edit {
                            putLong("ResourceLastSyncTime", timeProvider.now())
                            putInt("ResourceSyncPosition", skip)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SyncManager", "Resource batch failed", e)
                    batchSizer.recordFailure()
                    hadBatchFailure = true
                    syncTimeLogger.logDetail("resource_sync", "Batch $batchCount failed: ${e.message}")
                    skip += batchSize
                }
            }

            try {
                syncTimeLogger.startProcess("resource_cleanup")
                val cleanupStartTime = SystemClock.elapsedRealtime()
                val validNewIds = newIds.filter { !it.isNullOrBlank() }
                if (hadBatchFailure) {
                    syncTimeLogger.logDetail("resource_sync", "Skipping delete-cleanup: one or more batches failed, id list is incomplete")
                } else if (validNewIds.isNotEmpty() && validNewIds.size == newIds.size) {
                    resourcesRepository.removeDeletedResources(validNewIds)
                }
                val cleanupDuration = SystemClock.elapsedRealtime() - cleanupStartTime
                syncTimeLogger.endProcess("resource_cleanup")
                if (cleanupDuration > 100) {
                    syncTimeLogger.logDbOperation("delete_cleanup", "resources", cleanupDuration, newIds.size - validNewIds.size)
                }
            } catch (e: Exception) {
                Log.e("SyncManager", "Resource cleanup failed", e)
                syncTimeLogger.logDetail("resource_sync", "Cleanup failed: ${e.message}")
            }
            syncTimeLogger.endProcess("resource_sync_main", processedItems)

            val resourceSyncEndTime = SystemClock.elapsedRealtime()
            val resourceSyncTime = resourceSyncEndTime - resourceSyncStartTime
            val minutes = resourceSyncTime / 60000
            val seconds = (resourceSyncTime % 60000) / 1000
            Log.d("SyncPerf", "  ✓ Resources sync completed: ${minutes}m ${seconds}s - $processedItems items")
        } catch (e: Exception) {
            Log.e("SyncManager", "Resource sync failed", e)
            syncTimeLogger.endProcess("resource_sync_main", processedItems)
            val resourceSyncEndTime = SystemClock.elapsedRealtime()
            Log.d("SyncPerf", "  ✗ Resources sync failed after ${resourceSyncEndTime - resourceSyncStartTime}ms: ${e.message}")
        }
    }

    private fun handleException(message: String?) {
        if (listener != null) {
            isSyncing.set(false)
            MainApplication.syncFailedCount++
            listener?.onSyncFailed(message)
            _syncStatus.value = SyncStatus.Error(message ?: "Unknown error")
        }
    }

    private suspend fun getShelvesWithDataBatchOptimized(): List<String> {
        val shelvesWithData = mutableListOf<String>()
        val cachedShelves = getCachedShelvesWithData()
        if (cachedShelves.isNotEmpty()) {
            return cachedShelves
        }

        val url = UrlUtils.getUrl()
        val header = UrlUtils.header

        val allShelves = ApiClient.executeWithRetryAndWrap {
            apiInterface.getDocuments(header, "$url/shelf/_all_docs")
        }?.body()?.rows ?: return emptyList()

        coroutineScope {
            val semaphore = Semaphore(8)
            val checkJobs = allShelves.chunked(25).map { shelfBatch ->
                async(dispatcherProvider.io) {
                    semaphore.withPermit {
                        checkShelfBatchForDataOptimized(shelfBatch)
                    }
                }
            }

            checkJobs.awaitAll().flatten().let { validShelves ->
                shelvesWithData.addAll(validShelves)
            }
        }

        cacheShelvesWithData(shelvesWithData)
        return shelvesWithData
    }

    private suspend fun checkShelfBatchForDataOptimized(shelfBatch: List<Rows>): List<String> {
        return userSyncRepository.checkShelfBatchForDataOptimized(shelfBatch.mapNotNull { it.id })
    }

    private fun getCachedShelvesWithData(): List<String> {
        val cacheKey = "shelves_with_data"
        val cacheTimeKey = "shelves_cache_time"
        val cacheValidityHours = 6

        val cacheTime = sharedPrefManager.getRawLong(cacheTimeKey, 0)
        val now = timeProvider.now()

        if (now - cacheTime < cacheValidityHours * 60 * 60 * 1000) {
            val cachedData = sharedPrefManager.getRawString(cacheKey, "")
            if (cachedData.isNotEmpty()) {
                return cachedData.split(",").filter { it.isNotBlank() }
            }
        }
        return emptyList()
    }

    private fun cacheShelvesWithData(shelves: List<String>) {
        val cacheKey = "shelves_with_data"
        val cacheTimeKey = "shelves_cache_time"

        sharedPrefManager.setRawString(cacheKey, shelves.joinToString(","))
        sharedPrefManager.setRawLong(cacheTimeKey, timeProvider.now())
    }

    private suspend fun myLibraryTransactionSync() {

        val librarySyncStartTime = SystemClock.elapsedRealtime()
        Log.d("SyncPerf", "  ▶ Starting library sync")

        syncTimeLogger.startProcess("library_sync_main")
        var processedItems = 0

        try {
            syncTimeLogger.startProcess("library_get_shelves")
            val shelvesStartTime = SystemClock.elapsedRealtime()
            val shelvesWithData = getShelvesWithDataBatchOptimized()
            val shelvesDuration = SystemClock.elapsedRealtime() - shelvesStartTime
            syncTimeLogger.endProcess("library_get_shelves", shelvesWithData.size)
            Log.d("SyncPerf", "    Library: Found ${shelvesWithData.size} shelves with data in ${shelvesDuration}ms")

            if (shelvesWithData.isEmpty()) {
                syncTimeLogger.logDetail("library_sync", "No shelves with data found, skipping library sync")
                syncTimeLogger.endProcess("library_sync_main", 0)
                return
            }

            syncTimeLogger.startProcess("library_process_shelves")

            val completedShelves = AtomicInteger(0)
            coroutineScope {
                val semaphore = Semaphore(6)
                val shelfJobs = shelvesWithData.mapIndexed { index, shelfId ->
                    async(dispatcherProvider.io) {
                        semaphore.withPermit {
                            val shelfStartTime = SystemClock.elapsedRealtime()
                            val items = syncRepository.processShelfParallel(shelfId)
                            val shelfDuration = SystemClock.elapsedRealtime() - shelfStartTime
                            if (items > 0) {
                                syncTimeLogger.logDetail("library_sync", "Shelf ${index + 1}/${shelvesWithData.size} ($shelfId): $items items in ${shelfDuration}ms")
                            }
                            val completed = completedShelves.incrementAndGet()
                            _syncStatus.value = SyncStatus.Syncing(
                                context.getString(R.string.sync_phase_library), 3, 4,
                                completed, shelvesWithData.size,
                                context.getString(R.string.sync_shelves_progress, completed, shelvesWithData.size)
                            )
                            items
                        }
                    }
                }

                processedItems = shelfJobs.awaitAll().sum()
            }

            syncTimeLogger.endProcess("library_process_shelves", processedItems)

            saveConcatenatedLinksToPrefs(sharedPrefManager)
            syncTimeLogger.endProcess("library_sync_main", processedItems)

            val totalDuration = SystemClock.elapsedRealtime() - librarySyncStartTime
            Log.d("SyncPerf", "  ✓ Library sync completed: ${totalDuration}ms - $processedItems items from ${shelvesWithData.size} shelves")
        } catch (e: Exception) {
            Log.e("SyncManager", "Library sync failed", e)
            syncTimeLogger.endProcess("library_sync_main", processedItems)
            val failDuration = SystemClock.elapsedRealtime() - librarySyncStartTime
            Log.d("SyncPerf", "  ✗ Library sync failed after ${failDuration}ms: ${e.message}")
        }
    }
}
