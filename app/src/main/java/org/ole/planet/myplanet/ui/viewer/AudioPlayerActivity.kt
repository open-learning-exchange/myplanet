package org.ole.planet.myplanet.ui.viewer

import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.DefaultTimeBar
import com.bumptech.glide.Glide
import java.io.File
import java.util.regex.Pattern
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityAudioPlayerBinding
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.Utilities

class AudioPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAudioPlayerBinding
    private var exoPlayer: ExoPlayer? = null
    private var filePath: String? = null
    private var isFullPath = false
    private lateinit var playButton: ImageButton
    private lateinit var pauseButton: ImageButton


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtils.setupEdgeToEdge(this, binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        filePath = intent.getStringExtra("TOUCHED_FILE")
        isFullPath = intent.getBooleanExtra("isFullPath", false)

        val extractedFileName = FileUtils.nameWithoutExtension(filePath).toString()
        val resourceTitle = intent.getStringExtra("RESOURCE_TITLE") ?: "Unknown Artist"

        supportActionBar?.title = "Audio Player"
        supportActionBar?.subtitle = extractedFileName

        binding.trackTitle.text = extractedFileName
        binding.artistName.text = resourceTitle
        playButton = binding.playerView.findViewById(R.id.exo_play)
        pauseButton = binding.playerView.findViewById(R.id.exo_pause)

        val overlay = binding.playerView.findViewById<FrameLayout>(R.id.exo_overlay)


        val blurredImageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        Glide.with(this)
            .load(getThemeBackground()) // or from URL or filePath
            .into(blurredImageView)

        overlay.addView(blurredImageView, 0)
        val controller = binding.playerView.findViewById<View>(R.id.exo_controller)
        controller?.setBackgroundColor(android.graphics.Color.TRANSPARENT)


        initializeExoPlayer()

        setupPlayPauseButtons()
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun initializeExoPlayer() {
        val fullPath = resolveFullPath(filePath)

        try {
            exoPlayer = ExoPlayer.Builder(this).build().also { player ->
                binding.playerView.player = player
                player.setMediaItem(MediaItem.fromUri(fullPath))
                player.prepare()
                player.playWhenReady = true

                val controller = binding.playerView.findViewById<View>(R.id.exo_controller)
                controller?.setBackgroundColor(android.graphics.Color.TRANSPARENT)

                val timeBar = binding.playerView.findViewById<DefaultTimeBar>(
                    androidx.media3.ui.R.id.exo_progress
                )
                timeBar?.apply {
                    setPlayedColor(ContextCompat.getColor(this@AudioPlayerActivity, R.color.daynight_textColor))
                    setScrubberColor(ContextCompat.getColor(this@AudioPlayerActivity, R.color.daynight_textColor))
                    setBufferedColor(ContextCompat.getColor(this@AudioPlayerActivity, R.color.hint_color))
                    setUnplayedColor(ContextCompat.getColor(this@AudioPlayerActivity, R.color.disable_color))
                }

                player.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Utilities.toast(this@AudioPlayerActivity, "Unable to play audio.")
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            player.seekTo(0)
                        }
                    }
                })
            }
        } catch (e: Exception) {
            Utilities.toast(this, "Unable to play audio.")
        }
    }

    private fun resolveFullPath(originalPath: String?): String {
        return if (isFullPath) {
            originalPath ?: ""
        } else {
            val processedPath = originalPath?.let {
                val uuidPattern = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/")
                val matcher = uuidPattern.matcher(it)
                if (matcher.find()) it.substring(matcher.end()) else it
            }

            File(getExternalFilesDir(null), "ole/$processedPath").absolutePath
        }
    }

    private fun getThemeBackground(): Int {
        val isDarkMode = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return if (isDarkMode) R.drawable.bg_player_dark else R.drawable.bg_player_white
    }

    private fun setupPlayPauseButtons() {
        playButton.setOnClickListener {
            playAudio()
        }

        pauseButton.setOnClickListener {
            pauseAudio()
        }
    }

    private fun playAudio() {
        exoPlayer?.play()
        playButton.visibility = View.GONE
        pauseButton.visibility = View.VISIBLE
    }

    private fun pauseAudio() {
        exoPlayer?.pause()
        pauseButton.visibility = View.GONE
        playButton.visibility = View.VISIBLE
    }



    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }


    override fun onPause() {
        super.onPause()
        pauseAudio()
    }

    override fun onDestroy() {
        binding.playerView.player = null
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }
}
