package org.ole.planet.myplanet.services

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.di.CoreDependenciesEntryPoint
import org.ole.planet.myplanet.di.getBroadcastService
import org.ole.planet.myplanet.model.Download

/**
 * Handles the Stop action on the ongoing download notification, so a download can be stopped from
 * anywhere, not only from the progress dialog of the screen that started it.
 */
class DownloadStopReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STOP_DOWNLOADS) return
        context.stopService(Intent(context, DownloadService::class.java))
        // Downloads fall back to DownloadWorker when the service cannot start; stop those too.
        WorkManager.getInstance(context).cancelAllWorkByTag(DOWNLOAD_WORK_TAG)

        // A screen may still be showing the progress dialog; a failed update with no message is what
        // makes it close quietly instead of waiting on progress that will never come.
        val pendingResult: PendingResult? = goAsync()
        val scope = EntryPointAccessors
            .fromApplication(context.applicationContext, CoreDependenciesEntryPoint::class.java)
            .applicationScope()
        scope.launch {
            try {
                val stopped = Intent(DownloadService.MESSAGE_PROGRESS).apply {
                    putExtra("download", Download().apply { failed = true })
                }
                getBroadcastService(context).sendBroadcast(stopped)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        const val ACTION_STOP_DOWNLOADS = "org.ole.planet.myplanet.action.STOP_DOWNLOADS"
        private const val REQUEST_CODE = 4101
        private const val DOWNLOAD_WORK_TAG = "download_work"

        fun stopAction(context: Context): NotificationCompat.Action {
            val intent = Intent(context, DownloadStopReceiver::class.java).setAction(ACTION_STOP_DOWNLOADS)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return NotificationCompat.Action(R.drawable.ic_stop, context.getString(R.string.stop_download), pendingIntent)
        }
    }
}
