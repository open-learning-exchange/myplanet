package org.ole.planet.myplanet.service

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.realm.kotlin.ext.query
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.utilities.NotificationUtil.create
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import java.util.Calendar

class TaskNotificationWorker(private val context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        val mRealm = DatabaseService().realmInstance
        val current = Calendar.getInstance().timeInMillis
        val tomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }

        UserProfileDbHandler(context).userModel?.let { user ->
            try {
                val tasks = mRealm.query<RealmTeamTask>("completed == false AND assignee == $0 AND isNotified == false AND deadline BETWEEN {$1, $2}", user.id, current, tomorrow.timeInMillis).find()

                mRealm.writeBlocking {
                    tasks.forEach { task ->
                        create(context, R.drawable.ole_logo, task.title, "Task expires on ${formatDate(task.deadline, "")}")
                        findLatest(task)?.let { latestTask ->
                            latestTask.isNotified = true
                        }
                    }
                }
                return Result.success()
            } catch (e: Exception) {
                e.printStackTrace()
                return Result.failure()
            } finally {
                mRealm.close()
            }
        }

        return Result.failure()
    }
}
