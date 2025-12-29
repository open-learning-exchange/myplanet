package org.ole.planet.myplanet.ui.viewer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
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
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.data.auth.AuthSessionUpdater
import org.ole.planet.myplanet.databinding.ActivityExoPlayerVideoBinding
import org.ole.planet.myplanet.utilities.DownloadUtils
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class VideoPlayerActivity : AppCompatActivity(), AuthSessionUpdater.AuthCallback {
    private lateinit var binding: ActivityExoPlayerVideoBinding
    private var exoPlayer: ExoPlayer? = null
    private var auth: String = ""
    private var videoURL: String = ""
    private var videoType: String? = null
    private var playWhenReady = true
    private var currentPosition = 0L
    private var isActivityVisible = false
    private var isAudioReceiverRegistered = false
    @Inject
    lateinit var authSessionUpdaterFactory: AuthSessionUpdater.Factory
    private var authSessionUpdater: AuthSessionUpdater? = null

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
        EdgeToEdgeUtils.setupEdgeToEdge(this, binding.root)

        val extras = intent.extras
        videoType = extras?.getString("videoType")
        videoURL = extras?.getString("videoURL") ?: ""
        auth = extras?.getString("Auth") ?: ""

        registerAudioNoisyReceiver()

        when (videoType) {
            "offline" -> prepareExoPlayerFromFileUri(videoURL)
            "online" -> {
                authSessionUpdater = authSessionUpdaterFactory.create(this)
            }
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
        runOnUiThread {
            streamVideoFromUrl(videoURL, auth)
            if (videoType == "online" && !FileUtils.checkFileExist(this, videoURL)) {
                try {
                    DownloadUtils.openDownloadService(this, arrayListOf(videoURL), false)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
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
                videoURL.startsWith("http") -> {
                    streamVideoFromUrl(videoURL, auth)
                    if (videoType == "online" && !FileUtils.checkFileExist(this, videoURL)) {
                        try {
                            DownloadUtils.openDownloadService(this, arrayListOf(videoURL), false)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
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

        val videoUri = videoUrl.toUri()
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

        val uri = uriString.toUri()
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
        if (!isAudioReceiverRegistered) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            registerReceiver(audioBecomingNoisyReceiver, filter)
            isAudioReceiverRegistered = true
        }
    }

    override fun onDestroy() {
        authSessionUpdater?.stop()
        if (isAudioReceiverRegistered) {
            unregisterReceiver(audioBecomingNoisyReceiver)
            isAudioReceiverRegistered = false
        }
        super.onDestroy()
    }
}
