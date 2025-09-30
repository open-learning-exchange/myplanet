package org.ole.planet.myplanet.service

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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.ManagerSync
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.service.sync.AdaptiveBatchProcessor
import org.ole.planet.myplanet.service.sync.OptimizedSyncStrategy
import org.ole.planet.myplanet.service.sync.RealmPoolManager
import org.ole.planet.myplanet.service.sync.StandardSyncStrategy
import org.ole.planet.myplanet.service.sync.SyncErrorRecovery
import org.ole.planet.myplanet.service.sync.SyncMode
import org.ole.planet.myplanet.service.sync.SyncPerformanceMonitor
import org.ole.planet.myplanet.service.sync.SyncStrategy
import org.ole.planet.myplanet.utilities.NotificationUtils
import org.ole.planet.myplanet.utilities.SyncTimeLogger

@Singleton
class ImprovedSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseService: DatabaseService,
    @AppPreferences private val settings: SharedPreferences,
    private val apiInterface: ApiInterface
) {
    
    private val batchProcessor = AdaptiveBatchProcessor(context)
    private val poolManager = RealmPoolManager.getInstance()
    private val errorRecovery = SyncErrorRecovery()
    private val performanceMonitor = SyncPerformanceMonitor(context)
    
    private val standardStrategy = StandardSyncStrategy()
    private val optimizedStrategy = OptimizedSyncStrategy(
        apiInterface, batchProcessor, poolManager, errorRecovery
    )
    
    private var isSyncing = false
    private var listener: SyncListener? = null
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
        listener: SyncListener?,
        syncMode: SyncMode = SyncMode.Standard,
        syncTables: List<String>? = null
    ) {
        this.listener = listener
        if (!isSyncing) {
            settings.edit { remove("concatenated_links") }
            listener?.onSyncStarted()
            startSyncProcess(syncMode, syncTables)
        }
    }
    
    private fun startSyncProcess(syncMode: SyncMode, syncTables: List<String>?) {
        syncScope.launch {
            try {
                if (TransactionSyncManager.authenticate()) {
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
        
        val tablesToSync = syncTables ?: syncOrder
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
        ManagerSync.instance?.syncAdmin()
        logger.endProcess("admin_sync")
        
        poolManager.useRealm { realm ->
            logger.startProcess("on_synced")
            org.ole.planet.myplanet.model.RealmResourceActivity.onSynced(realm, settings)
            logger.endProcess("on_synced")
        }
        
        logger.stopLogging()
    }
    
    private suspend fun syncTable(table: String, strategy: SyncStrategy, logger: SyncTimeLogger) {
        val config = batchProcessor.getOptimalConfig(table)
        val tracker = performanceMonitor.startSyncTracking(table, strategy.getStrategyName(), config)
        
        try {
            logger.startProcess("${table}_sync")
            
            if (strategy.isSupported(table)) {
                poolManager.useRealm { realm ->
                    strategy.syncTable(table, realm, config)
                        .onEach { result ->
                            tracker.incrementProcessedItems(result.processedItems)
                        }
                        .collect()
                }
            } else {
                // Fallback to standard sync
                poolManager.useRealm { realm ->
                    TransactionSyncManager.syncDb(realm, table)
                }
            }
            
            tracker.complete(success = true)
            logger.endProcess("${table}_sync")
            
        } catch (e: Exception) {
            tracker.complete(success = false, errorMessage = e.message)
            logger.endProcess("${table}_sync")
            
            // Try fallback strategy if optimized fails
            if (strategy != standardStrategy && settings.getBoolean("enableSyncFallback", true)) {
                try {
                    logger.startProcess("${table}_fallback_sync")
                    poolManager.useRealm { realm ->
                        standardStrategy.syncTable(table, realm, config).collect()
                    }
                    logger.endProcess("${table}_fallback_sync")
                } catch (fallbackE: Exception) {
                    throw e // Throw original exception
                }
            } else {
                throw e
            }
        }
    }
    
    private fun getStrategy(syncMode: SyncMode): SyncStrategy {
        return when (syncMode) {
            SyncMode.Standard -> standardStrategy
            SyncMode.Fast, SyncMode.Optimized -> {
                // Use performance monitor to determine best strategy
                val tablesToSync = syncOrder.take(3) // Check a few key tables
                val shouldUseOptimized = tablesToSync.any { table ->
                    performanceMonitor.getRecommendedStrategy(table) == "optimized"
                }
                
                if (shouldUseOptimized) optimizedStrategy else standardStrategy
            }
            is SyncMode.Custom -> syncMode.strategy
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
        settings.edit { putLong("LastSync", Date().time) }
        NotificationUtils.cancel(context, 111)
        listener?.onSyncComplete()
    }
    
    private fun handleException(message: String) {
        if (listener != null) {
            isSyncing = false
            org.ole.planet.myplanet.MainApplication.syncFailedCount++
            listener?.onSyncFailed(message)
        }
    }
    
    // Compatibility methods for existing code
    fun start(listener: SyncListener?, type: String, syncTables: List<String>? = null) {
        val syncMode = when {
            type == "upload" -> SyncMode.Standard
            settings.getBoolean("fastSync", false) -> SyncMode.Optimized
            else -> SyncMode.Standard
        }
        start(listener, syncMode, syncTables)
    }
}
