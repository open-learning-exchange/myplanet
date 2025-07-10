package org.ole.planet.myplanet.ui.viewer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import java.util.regex.Pattern
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityAudioPlayerBinding
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.Utilities
import java.util.concurrent.TimeUnit
class AudioPlayerActivity : AppCompatActivity() {
    private lateinit var activityAudioPlayerBinding: ActivityAudioPlayerBinding
    private var exoPlayer: ExoPlayer? = null
    private var isFullPath = false
    private var filePath: String? = null
    private lateinit var extractedFileName: String
    private var isPlaying = false
    private val handler = Handler(Looper.getMainLooper())
    private var updateSeekBarRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityAudioPlayerBinding = ActivityAudioPlayerBinding.inflate(layoutInflater)
        setContentView(activityAudioPlayerBinding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        filePath = intent.getStringExtra("TOUCHED_FILE")
        isFullPath = intent.getBooleanExtra("isFullPath", false)

        extractedFileName = FileUtils.nameWithoutExtension(filePath).toString()

        setupUI()
        setupClickListeners()
        initializeExoPlayer()
    }

    private fun setupUI() {
        activityAudioPlayerBinding.trackTitle.text = extractedFileName

        val resourceTitle = intent.getStringExtra("RESOURCE_TITLE") ?: "Unknown Artist"
        activityAudioPlayerBinding.artistName.text = resourceTitle

        supportActionBar?.title = "Audio Player"
        supportActionBar?.subtitle = extractedFileName

        activityAudioPlayerBinding.seekBar.max = 100
        activityAudioPlayerBinding.seekBar.progress = 0
        activityAudioPlayerBinding.currentTime.text = "0:00"
        activityAudioPlayerBinding.totalTime.text = "0:00"
    }

    private fun setupClickListeners() {
        activityAudioPlayerBinding.playPauseButton.setOnClickListener {
            togglePlayPause()
        }

        activityAudioPlayerBinding.rewindButton.setOnClickListener {
            rewind()
        }

        activityAudioPlayerBinding.fastForwardButton.setOnClickListener {
            fastForward()
        }

        activityAudioPlayerBinding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    exoPlayer?.let { player ->
                        val duration = player.duration
                        if (duration > 0) {
                            val position = (progress * duration) / 100
                            player.seekTo(position)
                        }
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun initializeExoPlayer() {
        val fullPath: String? = if (isFullPath) {
            filePath
        } else {
            val processedPath = if (filePath?.matches(".*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}//.*".toRegex()) == true) {
                val uuidPattern = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/")
                val matcher = filePath?.let { uuidPattern.matcher(it) }
                if (matcher?.find() == true) {
                    filePath?.substring(matcher.group().length)
                } else filePath
            } else filePath

            val basePath = getExternalFilesDir(null)
            File(basePath, "ole/$processedPath").absolutePath
        }

        try {
            exoPlayer = ExoPlayer.Builder(this).build().apply {
                val mediaItem = MediaItem.fromUri(fullPath!!)
                setMediaItem(mediaItem)

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                updateTotalTime()
                                startUpdatingSeekBar()
                            }
                            Player.STATE_ENDED -> {
                                resetPlayButton()
                                stopUpdatingSeekBar()
                            }
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        this@AudioPlayerActivity.isPlaying = isPlaying
                        updatePlayPauseButton()
                        if (isPlaying) {
                            startUpdatingSeekBar()
                        } else {
                            stopUpdatingSeekBar()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Utilities.toast(this@AudioPlayerActivity, getString(R.string.unable_to_play_audio))
                    }
                })

                prepare()
            }

            activityAudioPlayerBinding.playerView.player = exoPlayer

        } catch (e: Exception) {
            Utilities.toast(this, getString(R.string.unable_to_play_audio))
        }
    }

    private fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    private fun rewind() {
        exoPlayer?.let { player ->
            val currentPosition = player.currentPosition
            val newPosition = maxOf(0, currentPosition - 10000) // 10 seconds
            player.seekTo(newPosition)
        }
    }

    private fun fastForward() {
        exoPlayer?.let { player ->
            val currentPosition = player.currentPosition
            val duration = player.duration
            val newPosition = minOf(duration, currentPosition + 10000) // 10 seconds
            player.seekTo(newPosition)
        }
    }

    private fun updatePlayPauseButton() {
        if (isPlaying) {
            activityAudioPlayerBinding.playPauseButton.setImageResource(R.drawable.ic_pause)
        } else {
            activityAudioPlayerBinding.playPauseButton.setImageResource(R.drawable.ic_play_arrow)
        }
    }

    private fun resetPlayButton() {
        activityAudioPlayerBinding.playPauseButton.setImageResource(R.drawable.ic_play_arrow)
        activityAudioPlayerBinding.seekBar.progress = 0
        activityAudioPlayerBinding.currentTime.text = "0:00"
    }

    private fun updateTotalTime() {
        exoPlayer?.let { player ->
            val duration = player.duration
            if (duration > 0) {
                activityAudioPlayerBinding.totalTime.text = formatTime(duration)
            }
        }
    }

    private fun startUpdatingSeekBar() {
        updateSeekBarRunnable = object : Runnable {
            override fun run() {
                exoPlayer?.let { player ->
                    val currentPosition = player.currentPosition
                    val duration = player.duration

                    if (duration > 0) {
                        val progress = ((currentPosition * 100) / duration).toInt()
                        activityAudioPlayerBinding.seekBar.progress = progress
                        activityAudioPlayerBinding.currentTime.text = formatTime(currentPosition)
                    }
                }
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(updateSeekBarRunnable!!)
    }

    private fun stopUpdatingSeekBar() {
        updateSeekBarRunnable?.let { runnable ->
            handler.removeCallbacks(runnable)
        }
    }

    private fun formatTime(timeMs: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeMs)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(timeMs) % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
    }

    override fun onStop() {
        super.onStop()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopUpdatingSeekBar()
        activityAudioPlayerBinding.playerView.player = null
        exoPlayer?.release()
        exoPlayer = null
    }
}