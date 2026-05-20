package org.ole.planet.myplanet.ui.viewer

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityTextfileViewerBinding
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.EdgeToEdgeUtils
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.TTSManager

@AndroidEntryPoint
class TextFileViewerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTextfileViewerBinding

    @Inject lateinit var dispatcherProvider: DispatcherProvider
    @Inject lateinit var ttsManager: TTSManager

    private var fileName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTextfileViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtils.setupEdgeToEdge(this, binding.root)
        renderTextFile()
        setupTts()
    }

    private fun renderTextFile() {
        fileName = intent.getStringExtra("TOUCHED_FILE")
        if (!fileName.isNullOrEmpty()) {
            binding.textFileName.text = FileUtils.nameWithoutExtension(fileName)
            binding.textFileName.visibility = View.VISIBLE
        } else {
            binding.textFileName.text = getString(R.string.message_placeholder, "No file selected")
            binding.textFileName.visibility = View.VISIBLE
        }
        renderTextFileThread()
    }

    private fun renderTextFileThread() {
        lifecycleScope.launch(dispatcherProvider.io) {
            try {
                val basePath = getExternalFilesDir(null)
                val file = File(basePath, "ole/$fileName")
                val text = file.readText()
                withContext(dispatcherProvider.main) {
                    binding.textFileContent.text = text
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupTts() {
        binding.fabTts.setOnClickListener {
            if (ttsManager.isSpeaking) {
                ttsManager.stop()
            } else {
                val text = binding.textFileContent.text?.toString()
                if (text.isNullOrBlank()) {
                    Toast.makeText(this, getString(R.string.tts_not_available), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                ttsManager.speak(text)
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
