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
    private var playWhenReady = true
    private var currentPosition = 0L

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

    override fun onResume() {
        super.onResume()
        if (exoPlayer == null) {
            when {
                videoURL.startsWith("http") -> streamVideoFromUrl(videoURL, auth)
                else -> prepareExoPlayerFromFileUri(videoURL)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        releasePlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun releasePlayer() {
        exoPlayer?.let { player ->
            playWhenReady = player.playWhenReady
            currentPosition = player.currentPosition
            player.release()
            exoPlayer = null
        }
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
        exoPlayer?.apply {
            setMediaSource(mediaSource)
            seekTo(currentPosition)
            playWhenReady = this@VideoPlayerActivity.playWhenReady
            prepare()
        }
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
            val factory = DataSource.Factory { fileDataSource }

            fileDataSource.uri?.let { uri ->
                val audioSource = ProgressiveMediaSource.Factory(factory)
                    .createMediaSource(MediaItem.fromUri(uri))

                binding.exoPlayerSimple.player = exoPlayer
                exoPlayer?.apply {
                    setMediaSource(audioSource)
                    seekTo(currentPosition)
                    playWhenReady = this@VideoPlayerActivity.playWhenReady
                    prepare()
                }
            }
        } catch (e: FileDataSource.FileDataSourceException) {
            e.printStackTrace()
        }
    }
}