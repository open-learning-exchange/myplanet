package org.ole.planet.myplanet.ui.viewer

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityTextfileViewerBinding
import org.ole.planet.myplanet.utilities.FileUtils
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

class TextFileViewerActivity : AppCompatActivity() {
    private lateinit var activityTextFileViewerBinding: ActivityTextfileViewerBinding
    private var fileName: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityTextFileViewerBinding = ActivityTextfileViewerBinding.inflate(layoutInflater)
        setContentView(activityTextFileViewerBinding.root)
        renderTextFile()
    }

    private fun renderTextFile() {
        val textFileOpenIntent = intent
        fileName = textFileOpenIntent.getStringExtra("TOUCHED_FILE")
        if (!fileName.isNullOrEmpty()) {
            activityTextFileViewerBinding.textFileName.text = FileUtils.nameWithoutExtension(fileName)
            activityTextFileViewerBinding.textFileName.visibility = View.VISIBLE
        } else {
            activityTextFileViewerBinding.textFileName.text = getString(R.string.message_placeholder, "No file selected")
            activityTextFileViewerBinding.textFileName.visibility = View.VISIBLE
        }
        renderTextFileThread()
    }
    private fun renderTextFileThread() {
        val openTextFileThread: Thread = object : Thread() {
            override fun run() {
                try {
                    val basePath = getExternalFilesDir(null)
                    val file = File(basePath, "ole/$fileName")
                    val text = StringBuilder()
                    val reader = BufferedReader(FileReader(file))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        text.append(line)
                        text.append('\n')
                    }
                    reader.close()
                    activityTextFileViewerBinding.textFileContent.text = text.toString()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        openTextFileThread.start()
    }
}