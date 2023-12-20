package org.ole.planet.myplanet.callback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity

class ProgressBroadcastReceiver : BroadcastReceiver() {
    private var onProgressChange: OnProgressChange? = null
    fun setOnProgressChange(onProgressChange: OnProgressChange?) {
        this.onProgressChange = onProgressChange
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DashboardActivity.MESSAGE_PROGRESS) {
            val download = intent.getParcelableExtra<Download>("download")
            if (onProgressChange != null) {
                onProgressChange!!.onProgressChange(
                    String.format(
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
