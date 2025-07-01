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
import androidx.media3.common.PlaybackParameters
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityAudioPlayerBinding
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.Utilities
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class AudioPlayerActivity : AppCompatActivity() {
    private lateinit var activityAudioPlayerBinding: ActivityAudioPlayerBinding
    private var exoPlayer: ExoPlayer? = null
    private var isFullPath = false
    private var filePath: String? = null
    private lateinit var extractedFileName: String
    private val handler = Handler(Looper.getMainLooper())

    private var isUserSeeking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityAudioPlayerBinding = ActivityAudioPlayerBinding.inflate(layoutInflater)
        setContentView(activityAudioPlayerBinding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        filePath = intent.getStringExtra("TOUCHED_FILE")
        isFullPath = intent.getBooleanExtra("isFullPath", false)

        extractedFileName = FileUtils.nameWithoutExtension(filePath).toString()

        setupUI()
        initializeExoPlayer()
        setupClickListeners()
    }

    private fun setupUI() {
        activityAudioPlayerBinding.trackTitle.text = extractedFileName

        val resourceTitle = intent.getStringExtra("RESOURCE_TITLE") ?: "Unknown Artist"
        activityAudioPlayerBinding.artistName.text = resourceTitle

        activityAudioPlayerBinding.currentTime.text = "00:00"
        activityAudioPlayerBinding.totalTime.text = "00:00"

        activityAudioPlayerBinding.playPauseButton.setImageResource(R.drawable.ic_play_arrow)
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
                                val duration = duration
                                if (duration > 0) {
                                    activityAudioPlayerBinding.seekBar.max = duration.toInt()
                                    activityAudioPlayerBinding.totalTime.text = formatTime(duration.toInt())
                                }
                                updateSeekBar()
                            }
                            Player.STATE_ENDED -> {
                                onAudioCompleted()
                            }
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) {
                            activityAudioPlayerBinding.playPauseButton.setImageResource(R.drawable.ic_pause)
                            updateSeekBar()
                        } else {
                            activityAudioPlayerBinding.playPauseButton.setImageResource(R.drawable.ic_play_arrow)
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Utilities.toast(this@AudioPlayerActivity, getString(R.string.unable_to_play_audio))
                    }
                })

                prepare()
            }
        } catch (e: Exception) {
            Utilities.toast(this, getString(R.string.unable_to_play_audio))
        }
    }

    private fun setupClickListeners() {
        activityAudioPlayerBinding.playPauseButton.setOnClickListener {
            togglePlayPause()
        }

        activityAudioPlayerBinding.rewindButton.setOnClickListener {
            exoPlayer?.let { player ->
                val currentPosition = player.currentPosition
                val newPosition = maxOf(0, currentPosition - 10000)
                player.seekTo(newPosition)
            }
        }

        activityAudioPlayerBinding.fastForwardButton.setOnClickListener {
            exoPlayer?.let { player ->
                val currentPosition = player.currentPosition
                val duration = player.duration
                val newPosition = if (duration > 0) minOf(duration, currentPosition + 10000) else currentPosition + 10000
                player.seekTo(newPosition)
            }
        }



        activityAudioPlayerBinding.volumeButton.setOnClickListener {
            val audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
            audioManager.adjustStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.ADJUST_SAME,
                android.media.AudioManager.FLAG_SHOW_UI
            )
        }

        activityAudioPlayerBinding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !isUserSeeking) {
                    activityAudioPlayerBinding.currentTime.text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let { bar ->
                    exoPlayer?.seekTo(bar.progress.toLong())
                }
                isUserSeeking = false
            }
        })
    }

    private fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.playbackState == Player.STATE_ENDED) {
                // If audio has ended, restart from beginning
                player.seekTo(0)
                player.play()
            } else if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }



    private fun updateSeekBar() {
        exoPlayer?.let { player ->
            if (player.isPlaying && !isUserSeeking) {
                val currentPosition = player.currentPosition.toInt()
                activityAudioPlayerBinding.seekBar.progress = currentPosition
                activityAudioPlayerBinding.currentTime.text = formatTime(currentPosition)
            }
            if (player.isPlaying) {
                handler.postDelayed({ updateSeekBar() }, 1000)
            }
        }
    }

    private fun formatTime(milliseconds: Int): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds.toLong())
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds.toLong()) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun onAudioCompleted() {
        activityAudioPlayerBinding.playPauseButton.setImageResource(R.drawable.ic_play_arrow)
        activityAudioPlayerBinding.seekBar.progress = 0
        activityAudioPlayerBinding.currentTime.text = "00:00"
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
        handler.removeCallbacksAndMessages(null)
        exoPlayer?.release()
        exoPlayer = null
    }
}