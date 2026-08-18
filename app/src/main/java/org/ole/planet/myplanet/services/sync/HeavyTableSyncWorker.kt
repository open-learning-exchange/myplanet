package org.ole.planet.myplanet.services.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class HeavyTableSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionSyncManager: TransactionSyncManager,
    private val syncManager: SyncManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val table = inputData.getString(KEY_TABLE) ?: return Result.failure()
        if (syncManager.isMainSyncActive()) return Result.retry()
        // All ALL_HEAVY_TABLES now sync incrementally via a since-cursor persisted after every
        // page (TransactionSyncManager.syncDbIncremental), so there's no separate "was this
        // interrupted mid-scan" checkpoint to inspect here: a real interruption throws out of
        // syncDb() as a CancellationException, which WorkManager handles via the coroutine's own
        // cancellation rather than this method returning a Result at all.
        transactionSyncManager.syncDb(table, useCheckpoint = true)
        return Result.success()
    }

    companion object {
        const val KEY_TABLE = "table"
        val ALL_HEAVY_TABLES = listOf(
            "ratings", "courses_progress", "submissions", "login_activities", "team_activities"
        )

        fun schedule(context: Context, tables: List<String> = ALL_HEAVY_TABLES) {
            val wm = WorkManager.getInstance(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            tables.forEach { table ->
                val request = OneTimeWorkRequestBuilder<HeavyTableSyncWorker>()
                    .setConstraints(constraints)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                    .setInputData(workDataOf(KEY_TABLE to table))
                    .build()
                wm.enqueueUniqueWork("heavy_sync_$table", ExistingWorkPolicy.KEEP, request)
            }
        }

        fun scheduleIfPending(context: Context) {
            // Heavy tables sync incrementally now (a persisted since-cursor per table), so a
            // catch-up run here is cheap even when nothing changed -- no need to gate on a
            // "was interrupted" pref that incremental syncs no longer write.
            schedule(context)
        }
    }
}
