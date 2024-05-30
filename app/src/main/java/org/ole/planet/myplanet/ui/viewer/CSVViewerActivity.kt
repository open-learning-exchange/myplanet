package org.ole.planet.myplanet.ui.viewer

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import org.ole.planet.myplanet.databinding.ActivityCsvviewerBinding
import org.ole.planet.myplanet.utilities.FileUtils
import java.io.File
import java.io.FileReader

class CSVViewerActivity : AppCompatActivity() {
    private lateinit var activityCsvviewerBinding: ActivityCsvviewerBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityCsvviewerBinding = ActivityCsvviewerBinding.inflate(layoutInflater)
        setContentView(activityCsvviewerBinding.root)
        renderCSVFile()
    }

    private fun renderCSVFile() {
        val csvFileOpenIntent = intent
        val fileName = csvFileOpenIntent.getStringExtra("TOUCHED_FILE")
        if (!fileName.isNullOrEmpty()) {
            activityCsvviewerBinding.csvFileName.text = FileUtils.nameWithoutExtension(fileName)
            activityCsvviewerBinding.csvFileName.visibility = View.VISIBLE
        } else {
            activityCsvviewerBinding.csvFileName.text = "No file selected"
            activityCsvviewerBinding.csvFileName.visibility = View.VISIBLE
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
                activityCsvviewerBinding.csvFileContent.append(row.contentToString())
                activityCsvviewerBinding.csvFileContent.append("\n")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}