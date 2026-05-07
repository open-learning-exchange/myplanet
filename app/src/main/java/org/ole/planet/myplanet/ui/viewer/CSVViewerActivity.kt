package org.ole.planet.myplanet.ui.viewer

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileReader
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityCsvviewerBinding
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.EdgeToEdgeUtils
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.TTSManager

@AndroidEntryPoint
class CSVViewerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCsvviewerBinding

    @Inject lateinit var dispatcherProvider: DispatcherProvider
    @Inject lateinit var ttsManager: TTSManager

    private val csvRows = mutableListOf<Array<String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCsvviewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtils.setupEdgeToEdge(this, binding.root)
        renderCSVFile()
        setupTts()
    }

    private fun renderCSVFile() {
        val fileName = intent.getStringExtra("TOUCHED_FILE")
        if (!fileName.isNullOrEmpty()) {
            binding.csvFileName.text = FileUtils.nameWithoutExtension(fileName)
            binding.csvFileName.visibility = View.VISIBLE
        } else {
            binding.csvFileName.text = getString(R.string.message_placeholder, "No file selected")
            binding.csvFileName.visibility = View.VISIBLE
        }

        binding.csvProgressBar.visibility = View.VISIBLE
        binding.csvFileContent.text = ""

        lifecycleScope.launch(dispatcherProvider.io) {
            try {
                val csvFile: File = if (fileName?.startsWith("/") == true) {
                    File(fileName)
                } else {
                    val basePath = getExternalFilesDir(null)
                    File(basePath, "ole/$fileName")
                }
                val reader = CSVReaderBuilder(FileReader(csvFile))
                    .withCSVParser(CSVParserBuilder().withSeparator(',').withQuoteChar('"').build())
                    .build()

                val chunkSize = 100
                val chunk = mutableListOf<Array<String>>()

                reader.use { csvReader ->
                    for (row in csvReader) {
                        chunk.add(row)
                        csvRows.add(row)
                        if (chunk.size >= chunkSize) {
                            val spannableChunk = buildSpannableForChunk(chunk)
                            withContext(dispatcherProvider.main) {
                                binding.csvFileContent.append(spannableChunk)
                            }
                            chunk.clear()
                            yield()
                        }
                    }

                    if (chunk.isNotEmpty()) {
                        val spannableChunk = buildSpannableForChunk(chunk)
                        withContext(dispatcherProvider.main) {
                            binding.csvFileContent.append(spannableChunk)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(dispatcherProvider.main) {
                    binding.csvFileContent.text = "Error reading file: ${e.message}"
                }
            } finally {
                withContext(dispatcherProvider.main) {
                    binding.csvProgressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun buildSpannableForChunk(chunk: List<Array<String>>): SpannableStringBuilder {
        val spannableContent = SpannableStringBuilder()
        for (row in chunk) {
            val rowText = row.contentToString() + "\n"
            val start = spannableContent.length
            spannableContent.append(rowText)
            spannableContent.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this, R.color.daynight_textColor)),
                start, spannableContent.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return spannableContent
    }

    private fun setupTts() {
        binding.fabTts.setOnClickListener {
            if (ttsManager.isSpeaking) {
                ttsManager.stop()
            } else {
                if (csvRows.isEmpty()) {
                    Toast.makeText(this, getString(R.string.tts_not_available), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                ttsManager.speak(TTSManager.formatCsvForSpeech(csvRows))
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
