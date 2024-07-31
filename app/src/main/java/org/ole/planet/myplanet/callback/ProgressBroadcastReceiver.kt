package org.ole.planet.myplanet.callback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import java.util.Locale

class ProgressBroadcastReceiver : BroadcastReceiver() {
    private var onProgressChange: OnProgressChange? = null

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DashboardActivity.MESSAGE_PROGRESS) {
            val download: Download? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("download", Download::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("download")
            }
            if (onProgressChange != null) {
                onProgressChange!!.onProgressChange(
                    String.format(
                        Locale.getDefault(),
                        "Downloading file %d/%d KB\n%d%% Completed.",
                        download!!.currentFileSize, download.totalFileSize, download.progress
                    )
                )
            }
        }
    }

    interface OnProgressChange {
        fun onProgressChange(s: String?)
    }
}
