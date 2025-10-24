package org.ole.planet.myplanet.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import java.util.Calendar
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.di.WorkerDependenciesEntryPoint
import org.ole.planet.myplanet.utilities.NotificationUtils.create
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate

class TaskNotificationWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val current = Calendar.getInstance().timeInMillis
        val tomorrow = Calendar.getInstance()
        tomorrow.add(Calendar.DAY_OF_YEAR, 1)

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WorkerDependenciesEntryPoint::class.java
        )
        val userProfileDbHandler = entryPoint.userProfileDbHandler()
        val teamRepository = entryPoint.teamRepository()

        val user = userProfileDbHandler.userModel
        val userId = user?.id
        if (!userId.isNullOrBlank()) {
            val tasks = runCatching {
                teamRepository.getPendingTasksForUser(userId, current, tomorrow.timeInMillis)
            }.getOrElse { emptyList() }

            if (tasks.isNotEmpty()) {
                tasks.forEach { task ->
                    create(
                        applicationContext,
                        R.drawable.ole_logo,
                        task.title,
                        "Task expires on " + formatDate(task.deadline, ""),
                    )
                }

                val taskIds = tasks.mapNotNull { it.id }.filter { it.isNotBlank() }
                if (taskIds.isNotEmpty()) {
                    runCatching { teamRepository.markTasksNotified(taskIds) }
                }
            }
        }
        return Result.success()
    }
}
