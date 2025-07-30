package org.ole.planet.myplanet.ui.viewer

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityTextfileViewerBinding
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtil
import org.ole.planet.myplanet.utilities.FileUtils

class TextFileViewerActivity : AppCompatActivity() {
    private lateinit var activityTextFileViewerBinding: ActivityTextfileViewerBinding
    private var fileName: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityTextFileViewerBinding = ActivityTextfileViewerBinding.inflate(layoutInflater)
        setContentView(activityTextFileViewerBinding.root)
        EdgeToEdgeUtil.setupEdgeToEdge(this, activityTextFileViewerBinding.root)
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
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val basePath = getExternalFilesDir(null)
                val file = File(basePath, "ole/$fileName")
                val text = file.readText()
                withContext(Dispatchers.Main) {
                    activityTextFileViewerBinding.textFileContent.text = text
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
