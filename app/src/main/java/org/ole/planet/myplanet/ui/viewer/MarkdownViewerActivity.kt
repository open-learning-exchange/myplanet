package org.ole.planet.myplanet.ui.viewer

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityMarkdownViewerBinding
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText

class MarkdownViewerActivity : AppCompatActivity() {
    private lateinit var activityMarkdownViewerBinding: ActivityMarkdownViewerBinding
    private var fileName: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityMarkdownViewerBinding = ActivityMarkdownViewerBinding.inflate(layoutInflater)
        setContentView(activityMarkdownViewerBinding.root)
        renderMarkdownFile()
    }

    private fun renderMarkdownFile() {
        val markdownOpenIntent = intent
        fileName = markdownOpenIntent.getStringExtra("TOUCHED_FILE")
        if (!fileName.isNullOrEmpty()) {

            activityMarkdownViewerBinding.markdownFileName.text = FileUtils.nameWithoutExtension(fileName)
            activityMarkdownViewerBinding.markdownFileName.visibility = View.VISIBLE
        } else {
            activityMarkdownViewerBinding.markdownFileName.text = getString(R.string.message_placeholder, "No file selected")
            activityMarkdownViewerBinding.markdownFileName.visibility = View.VISIBLE
        }
        try {
            val basePath = getExternalFilesDir(null)
            val markdownFile = File(basePath, "ole/$fileName")
            if (markdownFile.exists()) {
                val markdownContent = readMarkdownFile(markdownFile)
                setMarkdownText(activityMarkdownViewerBinding.markdownView, markdownContent)
            } else {
                Toast.makeText(this, getString(R.string.unable_to_load) + fileName, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun readMarkdownFile(file: File): String {
        val reader = BufferedReader(FileReader(file))
        val content = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            content.append(line).append("\n")
        }
        reader.close()
        return content.toString()
    }
}
