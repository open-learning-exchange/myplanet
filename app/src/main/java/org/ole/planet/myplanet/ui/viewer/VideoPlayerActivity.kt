package org.ole.planet.myplanet.ui.viewer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
    private var isActivityVisible = false

    private val audioBecomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                exoPlayer?.pause()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExoPlayerVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val extras = intent.extras
        val videoType = extras?.getString("videoType")
        videoURL = extras?.getString("videoURL") ?: ""
        auth = extras?.getString("Auth") ?: ""

        registerAudioNoisyReceiver()

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

    override fun onStart() {
        super.onStart()
        isActivityVisible = true
    }

    override fun onResume() {
        super.onResume()
        isActivityVisible = true
        if (exoPlayer == null) {
            when {
                videoURL.startsWith("http") -> streamVideoFromUrl(videoURL, auth)
                else -> prepareExoPlayerFromFileUri(videoURL)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        isActivityVisible = false
        pauseAndReleasePlayer()
    }

    override fun onStop() {
        super.onStop()
        isActivityVisible = false
        pauseAndReleasePlayer()
    }

    private fun pauseAndReleasePlayer() {
        exoPlayer?.pause()
        releasePlayer()
    }

    private fun releasePlayer() {
        exoPlayer?.let { player ->
            try {
                playWhenReady = player.playWhenReady
                currentPosition = player.currentPosition
                player.stop()
                player.clearMediaItems()
                player.release()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                exoPlayer = null
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun streamVideoFromUrl(videoUrl: String, auth: String) {
        if (!isActivityVisible) return

        val trackSelectorDef = DefaultTrackSelector(this)

        exoPlayer = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelectorDef)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .build()

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
        if (!isActivityVisible) return

        val uri = Uri.parse(uriString)
        val trackSelectorDef = DefaultTrackSelector(this)

        exoPlayer = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelectorDef)
            .setLoadControl(DefaultLoadControl())
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
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

    private fun registerAudioNoisyReceiver() {
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(audioBecomingNoisyReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(audioBecomingNoisyReceiver)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }
}