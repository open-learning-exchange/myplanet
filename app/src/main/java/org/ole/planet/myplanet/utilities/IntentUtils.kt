    package org.ole.planet.myplanet.utilities

    import android.content.Context
    import android.content.Intent
    import org.ole.planet.myplanet.ui.viewer.AudioPlayerActivity

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
    }