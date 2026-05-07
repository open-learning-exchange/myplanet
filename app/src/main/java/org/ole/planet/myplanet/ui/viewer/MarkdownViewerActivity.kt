package org.ole.planet.myplanet.ui.viewer

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityMarkdownViewerBinding
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.EdgeToEdgeUtils
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.MarkdownUtils.setMarkdownText
import org.ole.planet.myplanet.utils.TTSManager

@AndroidEntryPoint
class MarkdownViewerActivity : AppCompatActivity() {
    @Inject lateinit var dispatcherProvider: DispatcherProvider
    @Inject lateinit var ttsManager: TTSManager

    private lateinit var binding: ActivityMarkdownViewerBinding
    private var fileName: String? = null
    private var rawMarkdownText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMarkdownViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtils.setupEdgeToEdge(this, binding.root)
        renderMarkdownFile()
        setupTts()
    }

    private fun renderMarkdownFile() {
        fileName = intent.getStringExtra("TOUCHED_FILE")
        if (!fileName.isNullOrEmpty()) {
            binding.markdownFileName.text = FileUtils.nameWithoutExtension(fileName)
            binding.markdownFileName.visibility = View.VISIBLE
        } else {
            binding.markdownFileName.text = getString(R.string.message_placeholder, "No file selected")
            binding.markdownFileName.visibility = View.VISIBLE
        }
        lifecycleScope.launch(dispatcherProvider.io) {
            try {
                val basePath = getExternalFilesDir(null)
                val markdownFile = File(basePath, "ole/$fileName")
                if (markdownFile.exists()) {
                    val markdownContent = readMarkdownFile(markdownFile)
                    withContext(dispatcherProvider.main) {
                        rawMarkdownText = markdownContent
                        setMarkdownText(binding.markdownView, markdownContent)
                    }
                } else {
                    withContext(dispatcherProvider.main) {
                        Toast.makeText(
                            this@MarkdownViewerActivity,
                            getString(R.string.unable_to_load) + fileName,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Throws(IOException::class)
    private fun readMarkdownFile(file: File): String {
        return file.useLines { sequence ->
            sequence.joinToString("\n")
        }
    }

    private fun setupTts() {
        binding.fabTts.setOnClickListener {
            if (ttsManager.isSpeaking) {
                ttsManager.stop()
            } else {
                val plainText = TTSManager.stripMarkdown(rawMarkdownText)
                if (plainText.isBlank()) {
                    Toast.makeText(this, getString(R.string.tts_not_available), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                ttsManager.speak(plainText)
            }
        }
        lifecycleScope.launch {
            ttsManager.state.collect { state ->
                binding.fabTts.setImageResource(
                    if (state == TTSManager.State.SPEAKING) R.drawable.ic_stop else R.drawable.ic_play
                )
                binding.fabTts.contentDescription = getString(
                    if (state == TTSManager.State.SPEAKING) R.string.stop_reading else R.string.read_aloud
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        ttsManager.stop()
    }
}
