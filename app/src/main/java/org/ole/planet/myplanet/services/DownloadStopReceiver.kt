package org.ole.planet.myplanet.services

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.di.CoreDependenciesEntryPoint
import org.ole.planet.myplanet.di.getBroadcastService
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.utils.DownloadUtils

class DownloadStopReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STOP_DOWNLOADS) return
        DownloadUtils.stopDownloads(context)

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
