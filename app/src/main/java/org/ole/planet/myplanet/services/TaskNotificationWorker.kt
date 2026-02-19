package org.ole.planet.myplanet.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.repository.TeamTaskRepository
import org.ole.planet.myplanet.utils.NotificationUtils.create
import org.ole.planet.myplanet.utils.TimeUtils.formatDate

@HiltWorker
class TaskNotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val userSessionManager: UserSessionManager,
    private val teamTaskRepository: TeamTaskRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val current = Calendar.getInstance().timeInMillis
        val tomorrow = Calendar.getInstance()
        tomorrow.add(Calendar.DAY_OF_YEAR, 1)

        val user = userSessionManager.getUserModel()
        val userId = user?.id
        if (!userId.isNullOrBlank()) {
            val tasks = runCatching {
                teamTaskRepository.getPendingTasksForUser(userId, current, tomorrow.timeInMillis)
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
                    runCatching { teamTaskRepository.markTasksNotified(taskIds) }
                }
            }
        }
        return Result.success()
    }
}
