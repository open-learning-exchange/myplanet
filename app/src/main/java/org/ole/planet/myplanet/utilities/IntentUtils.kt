package org.ole.planet.myplanet.utilities

import android.content.Context
import android.content.Intent
import org.ole.planet.myplanet.ui.viewer.AudioPlayerActivity

object IntentUtils {
    fun openAudioFile(context: Context, path: String?) {
        val i = Intent(context, AudioPlayerActivity::class.java)
        i.putExtra("isFullPath", true)
        i.putExtra("TOUCHED_FILE", path)
        context.startActivity(i)
    }
}
