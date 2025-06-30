package org.ole.planet.myplanet.ui.viewer

import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityAudioPlayerBinding
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.Utilities
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class AudioPlayerActivity : AppCompatActivity() {
    private lateinit var activityAudioPlayerBinding: ActivityAudioPlayerBinding
    private var mediaPlayer: MediaPlayer? = null
    private var isFullPath = false
    private var filePath: String? = null
    private lateinit var extractedFileName: String
    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    private var playbackSpeed = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityAudioPlayerBinding = ActivityAudioPlayerBinding.inflate(layoutInflater)
        setContentView(activityAudioPlayerBinding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        filePath = intent.getStringExtra("TOUCHED_FILE")
        isFullPath = intent.getBooleanExtra("isFullPath", false)

        extractedFileName = FileUtils.nameWithoutExtension(filePath).toString()

        setupUI()
        initializeAudio()
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

    private fun initializeAudio() {
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
            mediaPlayer = MediaPlayer().apply {
                setAudioStreamType(AudioManager.STREAM_MUSIC)
                setDataSource(fullPath)
                prepareAsync()
                setOnPreparedListener { player ->
                    val duration = player.duration
                    activityAudioPlayerBinding.seekBar.max = duration
                    activityAudioPlayerBinding.totalTime.text = formatTime(duration)
                    updateSeekBar()
                }
                setOnCompletionListener {
                    onAudioCompleted()
                }
                setOnErrorListener { _, what, extra ->
                    Utilities.toast(this@AudioPlayerActivity, getString(R.string.unable_to_play_audio))
                    true
                }
            }
        } catch (e: IOException) {
            Utilities.toast(this, getString(R.string.unable_to_play_audio))
        }
    }

    private fun setupClickListeners() {
        activityAudioPlayerBinding.playPauseButton.setOnClickListener {
            togglePlayPause()
        }

        activityAudioPlayerBinding.rewindButton.setOnClickListener {
            mediaPlayer?.let { player ->
                val currentPosition = player.currentPosition
                val newPosition = maxOf(0, currentPosition - 10000)
                player.seekTo(newPosition)
                activityAudioPlayerBinding.seekBar.progress = newPosition
            }
        }

        activityAudioPlayerBinding.fastForwardButton.setOnClickListener {
            mediaPlayer?.let { player ->
                val currentPosition = player.currentPosition
                val duration = player.duration
                val newPosition = minOf(duration, currentPosition + 10000)
                player.seekTo(newPosition)
                activityAudioPlayerBinding.seekBar.progress = newPosition
            }
        }


        activityAudioPlayerBinding.volumeButton.setOnClickListener {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
        }

        activityAudioPlayerBinding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                    activityAudioPlayerBinding.currentTime.text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun togglePlayPause() {
        mediaPlayer?.let { player ->
            if (isPlaying) {
                player.pause()
                activityAudioPlayerBinding.playPauseButton.setImageResource(R.drawable.ic_play_arrow)
                isPlaying = false
            } else {
                player.start()
                activityAudioPlayerBinding.playPauseButton.setImageResource(R.drawable.ic_pause)
                isPlaying = true
                updateSeekBar()
            }
        }
    }

    private fun cyclePlaybackSpeed() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            playbackSpeed = when (playbackSpeed) {
                1.0f -> 1.25f
                1.25f -> 1.5f
                1.5f -> 2.0f
                else -> 1.0f
            }
            mediaPlayer?.playbackParams = mediaPlayer?.playbackParams?.setSpeed(playbackSpeed)!!
            Utilities.toast(this, "Speed: ${playbackSpeed}x")
        }
    }

    private fun updateSeekBar() {
        mediaPlayer?.let { player ->
            if (isPlaying) {
                val currentPosition = player.currentPosition
                activityAudioPlayerBinding.seekBar.progress = currentPosition
                activityAudioPlayerBinding.currentTime.text = formatTime(currentPosition)

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
        isPlaying = false
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
        if (isPlaying) {
            mediaPlayer?.pause()
            activityAudioPlayerBinding.playPauseButton.setImageResource(R.drawable.ic_play_arrow)
            isPlaying = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        mediaPlayer?.release()
        mediaPlayer = null
    }
}