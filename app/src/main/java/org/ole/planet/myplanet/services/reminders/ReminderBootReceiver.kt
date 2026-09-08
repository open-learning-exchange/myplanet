package org.ole.planet.myplanet.services.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.ole.planet.myplanet.services.TaskNotificationWorker

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val workRequest = OneTimeWorkRequestBuilder<TaskNotificationWorker>()
                .addTag("reminder_boot_reschedule")
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
