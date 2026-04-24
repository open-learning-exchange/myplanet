package org.ole.planet.myplanet.services.sync

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication.Companion.createLog
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.DeviceUserRepository
import org.ole.planet.myplanet.utils.NotificationUtils
import org.ole.planet.myplanet.utils.SyncTimeLogger

@Singleton
class ImprovedSyncManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val databaseService: DatabaseService,
    @param:AppPreferences private val settings: SharedPreferences,
    private val sharedPrefManager: org.ole.planet.myplanet.services.SharedPrefManager,
    private val transactionSyncManager: TransactionSyncManager,
    private val standardStrategy: StandardSyncStrategy,
    private val loginSyncManager: LoginSyncManager,
    private val activitiesRepository: ActivitiesRepository,
    private val deviceUserRepository: DeviceUserRepository
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
            sharedPrefManager.removeKey("concatenated_links")
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
            try {
                if (transactionSyncManager.authenticate()) {
                    performSync(syncMode, syncTables)
                } else {
                    handleException("Authentication failed")
                }
            } catch (e: Exception) {
                handleException(e.message ?: "Unknown error")
            } finally {
                cleanup()
            }
        }
    }

    private suspend fun performSync(syncMode: SyncMode, syncTables: List<String>?) {
        val logger = SyncTimeLogger
        logger.startLogging()

        initializeSync()

        val hasDeviceUsers = deviceUserRepository.hasDeviceUsers()
        val requestedTables = syncTables ?: syncOrder
        val tablesToSync = SyncPolicy.applyBootstrapPolicy(requestedTables, hasDeviceUsers)
        val strategy = getStrategy(syncMode)

        coroutineScope {
            val syncJobs = tablesToSync.map { table ->
                async {
                    syncTable(table, strategy, logger)
                }
            }

            syncJobs.awaitAll()
        }

        // Post-sync operations
        logger.startProcess("admin_sync")
        loginSyncManager.syncAdmin()
        logger.endProcess("admin_sync")

        logger.startProcess("on_synced")
        activitiesRepository.recordSyncActivity(settings.getString("userId", "") ?: "")
        logger.endProcess("on_synced")

        if (hasDeviceUsers) {
            syncScope.launch {
                try {
                    transactionSyncManager.syncDb("notifications")
                } catch (_: Exception) {
                }
            }
        }

        logger.stopLogging()
    }

    private suspend fun syncTable(table: String, strategy: SyncStrategy, logger: SyncTimeLogger) {
        val config = batchProcessor.getOptimalConfig(table)

        try {
            logger.startProcess("${table}_sync")

            if (strategy.isSupported(table)) {
                strategy.syncTable(table, config).collect()
            } else {
                // Fallback to standard sync
                transactionSyncManager.syncDb(table)
            }

            logger.endProcess("${table}_sync")

        } catch (e: Exception) {
            logger.endProcess("${table}_sync")

            throw e
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

    private fun cleanup() {
        isSyncing = false
        sharedPrefManager.setLastSync(Date().time)
        NotificationUtils.cancel(context, 111)
        listener?.onSyncComplete()
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
