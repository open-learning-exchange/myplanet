package org.ole.planet.myplanet.ui.viewer

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File
import java.util.regex.Pattern

@Composable
fun AudioPlayerScreen(
    filePath: String?,
    isFullPath: Boolean,
    resourceTitle: String?
) {
    val context = LocalContext.current
    val fullPath = remember(filePath, isFullPath) {
        resolveFullPath(context, filePath, isFullPath)
    }

    val exoPlayer = remember(fullPath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(fullPath))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = resourceTitle ?: "Audio Player", style = MaterialTheme.typography.titleLarge)
        AndroidView(factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
            }
        })
    }
}

private fun resolveFullPath(context: Context, originalPath: String?, isFullPath: Boolean): String {
    return if (isFullPath) {
        originalPath ?: ""
    } else {
        val processedPath = originalPath?.let {
            val uuidPattern = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/")
            val matcher = uuidPattern.matcher(it)
            if (matcher.find()) it.substring(matcher.end()) else it
        }
        File(context.getExternalFilesDir(null), "ole/$processedPath").absolutePath
    }
}
