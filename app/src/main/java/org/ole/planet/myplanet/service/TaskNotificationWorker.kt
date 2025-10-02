package org.ole.planet.myplanet.service

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import java.util.Calendar
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.di.WorkerDependenciesEntryPoint
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.utilities.NotificationUtils.create
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate

class TaskNotificationWorker(private val context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        val current = Calendar.getInstance().timeInMillis
        val tomorrow = Calendar.getInstance()
        tomorrow.add(Calendar.DAY_OF_YEAR, 1)

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WorkerDependenciesEntryPoint::class.java
        )
        val databaseService = entryPoint.databaseService()
        val userProfileDbHandler = entryPoint.userProfileDbHandler()

        val user = userProfileDbHandler.userModel
        if (user != null) {
            databaseService.withRealm { realm ->
                val tasks: List<RealmTeamTask> = realm.where(RealmTeamTask::class.java)
                    .equalTo("completed", false)
                    .equalTo("assignee", user.id)
                    .equalTo("isNotified", false)
                    .between("deadline", current, tomorrow.timeInMillis)
                    .findAll()
                realm.executeTransaction {
                    for (task in tasks) {
                        create(context, R.drawable.ole_logo, task.title, "Task expires on " + formatDate(task.deadline, ""))
                        task.isNotified = true
                    }
                }
            }
        }
        return Result.success()
    }
}
