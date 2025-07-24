package org.ole.planet.myplanet.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.NotificationUtil.create
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate

@HiltWorker
class TaskNotificationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val databaseService: DatabaseService,
    private val userProfileDbHandler: UserProfileDbHandler
) : Worker(context, workerParams) {
    override fun doWork(): Result {
        val mRealm = databaseService.realmInstance
        val current = Calendar.getInstance().timeInMillis
        val tomorrow = Calendar.getInstance()
        tomorrow.add(Calendar.DAY_OF_YEAR, 1)
        val user = userProfileDbHandler.userModel
        if (user != null) {
            val tasks: List<RealmTeamTask> = mRealm.where(RealmTeamTask::class.java)
                .equalTo("completed", false)
                .equalTo("assignee", user.id)
                .equalTo("isNotified", false)
                .between("deadline", current, tomorrow.timeInMillis)
                .findAll()
            mRealm.beginTransaction()
            for (`in` in tasks) {
                create(context, R.drawable.ole_logo, `in`.title, "Task expires on " + formatDate(`in`.deadline, ""))
                `in`.isNotified = true
            }
            mRealm.commitTransaction()
        }
        return Result.success()
    }
}
