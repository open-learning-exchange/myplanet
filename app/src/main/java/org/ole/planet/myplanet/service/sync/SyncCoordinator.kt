package org.ole.planet.myplanet.service.sync

import android.content.Context
import android.content.SharedPreferences
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication.Companion.createLog
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppPreferences private val settings: SharedPreferences,
    private val improvedSyncManager: Lazy<ImprovedSyncManager>,
    private val transactionSyncManager: TransactionSyncManager,
    private val syncStatusTracker: SyncStatusTracker,
    @ApplicationScope private val syncScope: CoroutineScope
) {
    private val initializationJob: Job by lazy {
        syncScope.launch {
            improvedSyncManager.get().initialize()
        }
    }

    // Callback interface for legacy sync execution
    interface LegacySyncExecutor {
        suspend fun executeLegacySync(type: String, syncTables: List<String>?)
        fun cleanupMainSync()
    }

    fun start(
        listener: OnSyncListener?,
        type: String,
        syncTables: List<String>? = null,
        legacySyncExecutor: LegacySyncExecutor
    ) {
        syncStatusTracker.listener = listener
        if (syncStatusTracker.syncStatus.value !is SyncStatusTracker.SyncStatus.Syncing) {
            syncStatusTracker.setStatus(SyncStatusTracker.SyncStatus.Idle)
            settings.edit().remove("concatenated_links").apply()
            syncStatusTracker.notifyStarted()
            syncStatusTracker.setStatus(SyncStatusTracker.SyncStatus.Syncing)

            val useImproved = settings.getBoolean("useImprovedSync", false)
            val isSyncRequest = type.equals("sync", ignoreCase = true)

            if (useImproved && isSyncRequest) {
                initializeAndStartImprovedSync(listener, syncTables)
            } else {
                if (useImproved && !isSyncRequest) {
                    createLog("sync_manager_route", "legacy|reason=$type")
                } else if (!useImproved) {
                    createLog("sync_manager_route", "legacy")
                }
                authenticateAndSync(type, syncTables, legacySyncExecutor)
            }
        }
    }

    private fun initializeAndStartImprovedSync(listener: OnSyncListener?, syncTables: List<String>?) {
        syncScope.launch {
            try {
                initializationJob.join()

                val manager = improvedSyncManager.get()
                val syncMode = if (settings.getBoolean("fastSync", false)) {
                    SyncMode.Fast
                } else {
                    SyncMode.Standard
                }
                createLog("sync_manager_route", "improved|mode=${syncMode.javaClass.simpleName}")
                manager.start(listener, syncMode, syncTables)
            } catch (e: Exception) {
                syncStatusTracker.notifyFailed(e.message)
                syncStatusTracker.setStatus(SyncStatusTracker.SyncStatus.Error(e.message ?: "Unknown error"))
            }
        }
    }

    private fun authenticateAndSync(type: String, syncTables: List<String>?, legacySyncExecutor: LegacySyncExecutor) {
        syncScope.launch(Dispatchers.IO) {
            if (transactionSyncManager.authenticate()) {
                legacySyncExecutor.executeLegacySync(type, syncTables)
            } else {
                val message = context.getString(R.string.invalid_configuration)
                syncStatusTracker.setStatus(SyncStatusTracker.SyncStatus.Error(message))
                syncStatusTracker.notifyFailed(message)
                legacySyncExecutor.cleanupMainSync()
            }
        }
    }
}
