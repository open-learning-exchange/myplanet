package org.ole.planet.myplanet.ui.viewer

import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityExoPlayerVideoBinding
import org.ole.planet.myplanet.utilities.AuthSessionUpdater
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.Utilities

class VideoPlayerActivity : AppCompatActivity(), AuthSessionUpdater.AuthCallback {
    private lateinit var binding: ActivityExoPlayerVideoBinding
    private var exoPlayer: ExoPlayer? = null
    private var auth: String = ""
    private var videoURL: String = ""
    private lateinit var settings: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExoPlayerVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val extras = intent.extras
        val videoType = extras?.getString("videoType")
        videoURL = extras?.getString("videoURL") ?: ""
        auth = extras?.getString("Auth") ?: ""

        when (videoType) {
            "offline" -> prepareExoPlayerFromFileUri(videoURL)
            "online" -> AuthSessionUpdater(this, settings)
        }

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                releasePlayer()
                finish()
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    override fun setAuthSession(responseHeader: Map<String, List<String>>) {
        val headerAuth = responseHeader["Set-Cookie"]?.get(0)?.split(";") ?: return
        auth = headerAuth[0]
        runOnUiThread { streamVideoFromUrl(videoURL, auth) }
    }

    override fun onError(s: String) {
        runOnUiThread { Utilities.toast(this, getString(R.string.connection_failed_reason) + s) }
    }

    @OptIn(UnstableApi::class)
    private fun streamVideoFromUrl(videoUrl: String, auth: String) {
        val trackSelectorDef = DefaultTrackSelector(this)
        exoPlayer = ExoPlayer.Builder(this).setTrackSelector(trackSelectorDef).build()

        val videoUri = Uri.parse(videoUrl)
        val requestProperties = hashMapOf("Cookie" to auth)
        val defaultHttpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("ExoPlayer")
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(requestProperties)

        val mediaSource: MediaSource = ProgressiveMediaSource.Factory(defaultHttpDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(videoUri))

        binding.exoPlayerSimple.player = exoPlayer
        exoPlayer?.setMediaSource(mediaSource)
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = true
    }

    @OptIn(UnstableApi::class)
    private fun prepareExoPlayerFromFileUri(uriString: String) {
        val uri = Uri.parse(uriString)
        exoPlayer = ExoPlayer.Builder(this)
            .setTrackSelector(DefaultTrackSelector(this))
            .setLoadControl(DefaultLoadControl())
            .build()

        val dataSpec = DataSpec(uri)
        val fileDataSource = FileDataSource()
        try {
            fileDataSource.open(dataSpec)
        } catch (e: FileDataSource.FileDataSourceException) {
            e.printStackTrace()
        }

        val factory = DataSource.Factory { fileDataSource }
        val audioSource: MediaSource?
        if (fileDataSource.uri != null) {
            audioSource = ProgressiveMediaSource.Factory(factory)
                .createMediaSource(MediaItem.fromUri(fileDataSource.uri!!))
            binding.exoPlayerSimple.player = exoPlayer
            exoPlayer?.setMediaSource(audioSource)
            exoPlayer?.prepare()
            exoPlayer?.playWhenReady = true
        }
    }

    override fun onResume() {
        super.onResume()
        exoPlayer?.playWhenReady = true
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.playWhenReady = false
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    private fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
    }
}