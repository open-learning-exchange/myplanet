package org.ole.planet.myplanet.ui.viewer

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import org.ole.planet.myplanet.databinding.ActivityCsvviewerBinding
import java.io.File
import java.io.FileReader
import java.util.Arrays

class CSVViewerActivity : AppCompatActivity() {
    private var activityCsvviewerBinding: ActivityCsvviewerBinding? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityCsvviewerBinding = ActivityCsvviewerBinding.inflate(layoutInflater)
        setContentView(activityCsvviewerBinding!!.root)
        renderCSVFile()
    }

    private fun renderCSVFile() {
        val csvFileOpenIntent = intent
        val fileName = csvFileOpenIntent.getStringExtra("TOUCHED_FILE")
        if (!fileName.isNullOrEmpty()) {
            activityCsvviewerBinding!!.csvFileName.text = fileName
            activityCsvviewerBinding!!.csvFileName.visibility = View.VISIBLE
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
                activityCsvviewerBinding!!.csvFileContent.append(Arrays.toString(row))
                activityCsvviewerBinding!!.csvFileContent.append("\n")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}