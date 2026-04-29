package org.ole.planet.myplanet.services.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.ole.planet.myplanet.utils.SyncTimeLogger

@Singleton
class TeamSyncHandler @Inject constructor(
    private val transactionSyncManager: TransactionSyncManager
) : TableGroupSyncHandler {

    override suspend fun syncFull(): GroupSyncResult = coroutineScope {
        val logger = SyncTimeLogger
        val jobs = listOf(
            "teams" to async {
                logger.startProcess("teams_sync")
                val count = transactionSyncManager.syncDb("teams")
                logger.endProcess("teams_sync")
                count
            },
            "tasks" to async {
                logger.startProcess("tasks_sync")
                val count = transactionSyncManager.syncDb("tasks")
                logger.endProcess("tasks_sync")
                count
            },
            "team_activities" to async {
                logger.startProcess("team_activities_sync")
                val count = transactionSyncManager.syncDb("team_activities")
                logger.endProcess("team_activities_sync")
                count
            }
        )

        val results = jobs.associate { it.first to it.second.await() }
        val total = results.values.sum()

        GroupSyncResult(itemsSynced = total, success = true, results = results)
    }

    override suspend fun syncFast(syncTables: List<String>?): GroupSyncResult = coroutineScope {
        val logger = SyncTimeLogger
        val jobs = mutableListOf<Pair<String, Deferred<Int>>>()

        if (syncTables?.contains("tablet_users") != false) {
            jobs.add("teams" to async {
                logger.startProcess("teams_sync")
                val count = transactionSyncManager.syncDb("teams")
                logger.endProcess("teams_sync")
                count
            })
        }

        if (syncTables?.contains("tasks") == true) {
            jobs.add("tasks" to async {
                logger.startProcess("tasks_sync")
                val count = transactionSyncManager.syncDb("tasks")
                logger.endProcess("tasks_sync")
                count
            })
        }

        if (syncTables?.contains("team_activities") == true) {
            jobs.add("team_activities" to async {
                logger.startProcess("team_activities_sync")
                val count = transactionSyncManager.syncDb("team_activities")
                logger.endProcess("team_activities_sync")
                count
            })
        }

        val results = jobs.associate { it.first to it.second.await() }
        val total = results.values.sum()

        GroupSyncResult(itemsSynced = total, success = true, results = results)
    }
}
