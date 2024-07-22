package org.ole.planet.myplanet.ui.viewer

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityCsvviewerBinding
import org.ole.planet.myplanet.utilities.FileUtils
import java.io.File
import java.io.FileReader

class CSVViewerActivity : AppCompatActivity() {
    private lateinit var activityCsvViewerBinding: ActivityCsvviewerBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityCsvViewerBinding = ActivityCsvviewerBinding.inflate(layoutInflater)
        setContentView(activityCsvViewerBinding.root)
        renderCSVFile()
    }

    private fun renderCSVFile() {
        val csvFileOpenIntent = intent
        val fileName = csvFileOpenIntent.getStringExtra("TOUCHED_FILE")
        if (!fileName.isNullOrEmpty()) {
            activityCsvViewerBinding.csvFileName.text = FileUtils.nameWithoutExtension(fileName)
            activityCsvViewerBinding.csvFileName.visibility = View.VISIBLE
        } else {
            activityCsvViewerBinding.csvFileName.text = getString(R.string.message_placeholder, "No file selected")
            activityCsvViewerBinding.csvFileName.visibility = View.VISIBLE
        }

        try {
            val csvFile: File = if (fileName!!.startsWith("/")) {
                File(fileName)
            } else {
                val basePath = getExternalFilesDir(null)
                File(basePath, "ole/$fileName")
            }
            val reader = CSVReaderBuilder(FileReader(csvFile)).withCSVParser(CSVParserBuilder()
                .withSeparator(',')
                .withQuoteChar('"')
                .build()
            ).build()
            val allRows = reader.readAll()
            for (row in allRows) {
                activityCsvViewerBinding.csvFileContent.append(row.contentToString())
                activityCsvViewerBinding.csvFileContent.append("\n")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}