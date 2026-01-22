package org.ole.planet.myplanet.service.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.ole.planet.myplanet.MainApplication.Companion.createLog
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.service.sync.AdaptiveBatchProcessor
import org.ole.planet.myplanet.service.sync.RealmPoolManager
import org.ole.planet.myplanet.service.sync.StandardSyncStrategy
import org.ole.planet.myplanet.service.sync.SyncMode
import org.ole.planet.myplanet.service.sync.SyncStrategy
import org.ole.planet.myplanet.utils.NotificationUtils
import org.ole.planet.myplanet.utils.SyncTimeLogger

@Singleton
class ImprovedSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseService: DatabaseService,
    @AppPreferences private val settings: SharedPreferences,
    private val transactionSyncManager: TransactionSyncManager,
    private val standardStrategy: StandardSyncStrategy
) {

    private val batchProcessor = AdaptiveBatchProcessor(context)
    private val poolManager = RealmPoolManager.getInstance()

    private var isSyncing = false
    private var listener: OnSyncListener? = null
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Table sync order for dependencies
    private val syncOrder = listOf(
        "tablet_users",
        "tags",
        "teams",
        "news",
        "library",
        "resources",
        "courses",
        "exams",
        "ratings",
        "courses_progress",
        "achievements",
        "submissions",
        "tasks",
        "login_activities",
        "meetups",
        "health",
        "certifications",
        "team_activities",
        "chat_history",
        "feedback"
    )

    suspend fun initialize() {
        poolManager.initializePool(context, databaseService)
    }

    fun start(
        listener: OnSyncListener?,
        syncMode: SyncMode = SyncMode.Standard,
        syncTables: List<String>? = null
    ) {
        this.listener = listener
        if (!isSyncing) {
            settings.edit { remove("concatenated_links") }
            listener?.onSyncStarted()
            createLog(
                "improved_sync_start",
                "mode=${syncMode.describe()}|tables=${syncTables?.joinToString() ?: "default"}"
            )
            startSyncProcess(syncMode, syncTables)
        }
    }

    private fun startSyncProcess(syncMode: SyncMode, syncTables: List<String>?) {
        syncScope.launch {
            var success = false
            try {
                if (transactionSyncManager.authenticate()) {
                    performSync(syncMode, syncTables)
                    success = true
                } else {
                    handleException("Authentication failed")
                }
            } catch (e: Exception) {
                handleException(e.message ?: "Unknown error")
            } finally {
                cleanup(success)
            }
        }
    }

    private suspend fun performSync(syncMode: SyncMode, syncTables: List<String>?) {
        val logger = SyncTimeLogger
        logger.startLogging()

        initializeSync()

        val tablesToSync = syncTables ?: syncOrder
        val strategy = getStrategy(syncMode)

        supervisorScope {
            val syncJobs = tablesToSync.map { table ->
                async {
                    var result = syncTable(table, strategy, logger)

                    // Retry logic for failed tables
                    if (!result.success) {
                        var attempt = 1
                        while (!result.success && attempt < 3) {
                            delay(1000L * attempt)
                            logger.logDetail(table, "Retrying sync (attempt ${attempt + 1})...")
                            result = syncTable(table, strategy, logger)
                            attempt++
                        }
                    }
                    result
                }
            }

            val results = syncJobs.awaitAll()

            val failures = results.filter { !it.success }
            if (failures.isNotEmpty()) {
                val failedTables = failures.joinToString { it.table }
                createLog("sync_summary", "Partial failure: $failedTables")
                throw RuntimeException("Partial sync failure: $failedTables")
            }
        }

        // Post-sync operations
        logger.startProcess("admin_sync")
        LoginSyncManager.instance.syncAdmin()
        logger.endProcess("admin_sync")

        poolManager.useRealm { realm ->
            logger.startProcess("on_synced")
            org.ole.planet.myplanet.model.RealmResourceActivity.onSynced(realm, settings)
            logger.endProcess("on_synced")
        }

        logger.stopLogging()
    }

    private suspend fun syncTable(table: String, strategy: SyncStrategy, logger: SyncTimeLogger): SyncResult {
        val config = batchProcessor.getOptimalConfig(table)
        val startTime = System.currentTimeMillis()

        try {
            logger.startProcess("${table}_sync")

            val result = if (strategy.isSupported(table)) {
                var lastResult: SyncResult? = null
                poolManager.useRealm { realm ->
                    strategy.syncTable(table, realm, config).collect {
                        lastResult = it
                    }
                }
                lastResult ?: throw IllegalStateException("No result from syncTable")
            } else {
                // Fallback to standard sync
                transactionSyncManager.syncDb(table, throwOnError = true)
                SyncResult(
                    table = table,
                    processedItems = -1,
                    success = true,
                    duration = System.currentTimeMillis() - startTime,
                    strategy = "fallback"
                )
            }

            logger.endProcess("${table}_sync")
            return result

        } catch (e: Exception) {
            logger.endProcess("${table}_sync")
            createLog("sync_table_failed", "table=$table|error=${e.message}")
            return SyncResult(
                table = table,
                processedItems = 0,
                success = false,
                errorMessage = e.message,
                duration = System.currentTimeMillis() - startTime,
                strategy = "failed"
            )
        }
    }

    private fun getStrategy(syncMode: SyncMode): SyncStrategy {
        return when (syncMode) {
            SyncMode.Standard -> standardStrategy
            SyncMode.Fast, SyncMode.Optimized -> standardStrategy
        }
    }

    private fun initializeSync() {
        isSyncing = true
        NotificationUtils.create(
            context,
            org.ole.planet.myplanet.R.mipmap.ic_launcher,
            "Syncing data",
            "Please wait..."
        )
    }

    private fun cleanup(success: Boolean) {
        isSyncing = false
        if (success) {
            settings.edit { putLong("LastSync", Date().time) }
            listener?.onSyncComplete()
        }
        NotificationUtils.cancel(context, 111)
    }

    private fun SyncMode.describe(): String {
        return when (this) {
            SyncMode.Standard -> "Standard"
            SyncMode.Fast -> "Fast"
            SyncMode.Optimized -> "Optimized"
        }
    }
    
    private fun handleException(message: String) {
        if (listener != null) {
            isSyncing = false
            org.ole.planet.myplanet.MainApplication.syncFailedCount++
            listener?.onSyncFailed(message)
        }
    }

}
