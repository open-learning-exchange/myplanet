package org.ole.planet.myplanet.ui.viewer

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import java.io.File
import java.io.FileReader
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
        EdgeToEdgeUtils.setupEdgeToEdgeWithNoPadding(this, binding.root)
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

            val allRows = reader.readAll()
            val spannableContent = SpannableStringBuilder()
            for (row in allRows) {
                val rowText = row.contentToString() + "\n"
                val start = spannableContent.length
                spannableContent.append(rowText)
                spannableContent.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(this, R.color.daynight_textColor)),
                    start, spannableContent.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            binding.csvFileContent.text = spannableContent
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
