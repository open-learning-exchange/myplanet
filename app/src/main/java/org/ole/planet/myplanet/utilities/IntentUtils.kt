package org.ole.planet.myplanet.utilities

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import org.ole.planet.myplanet.ui.reader.AudioPlayerActivity

object IntentUtils {
    @JvmStatic
    fun openAudioFile(context: Context, path: String?, resourceTitle: String? = null) {
        val intent = Intent(context, AudioPlayerActivity::class.java).apply {
            putExtra("isFullPath", true)
            putExtra("TOUCHED_FILE", path)
            putExtra("RESOURCE_TITLE", resourceTitle)
        }
        context.startActivity(intent)
    }

    @JvmStatic
    fun openPlayStore(context: Context) {
        val appPackageName = context.packageName
        val intent = Intent(Intent.ACTION_VIEW, "market://details?id=$appPackageName".toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                "https://play.google.com/store/apps/details?id=$appPackageName".toUri(),
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(webIntent)
        }
    }
}
