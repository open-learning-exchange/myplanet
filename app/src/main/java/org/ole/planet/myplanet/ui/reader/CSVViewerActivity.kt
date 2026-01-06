package org.ole.planet.myplanet.ui.reader

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import java.io.File
import java.io.FileReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityCsvviewerBinding
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils
import org.ole.planet.myplanet.utilities.FileUtils

class CSVViewerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCsvviewerBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCsvviewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtils.setupEdgeToEdge(this, binding.root)
        renderCSVFile()
    }

    private fun renderCSVFile() {
        val csvFileOpenIntent = intent
        val fileName = csvFileOpenIntent.getStringExtra("TOUCHED_FILE")
        if (!fileName.isNullOrEmpty()) {
            binding.csvFileName.text = FileUtils.nameWithoutExtension(fileName)
            binding.csvFileName.visibility = View.VISIBLE
        } else {
            binding.csvFileName.text = getString(R.string.message_placeholder, "No file selected")
            binding.csvFileName.visibility = View.VISIBLE
        }

        binding.csvProgressBar.visibility = View.VISIBLE
        binding.csvFileContent.text = ""

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
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
                            if (chunk.size >= chunkSize) {
                                val spannableChunk = buildSpannableForChunk(chunk)
                                withContext(Dispatchers.Main) {
                                    binding.csvFileContent.append(spannableChunk)
                                }
                                chunk.clear()
                                yield()
                            }
                        }

                        if (chunk.isNotEmpty()) {
                            val spannableChunk = buildSpannableForChunk(chunk)
                            withContext(Dispatchers.Main) {
                                binding.csvFileContent.append(spannableChunk)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.csvFileContent.text = "Error reading file: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
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
}
