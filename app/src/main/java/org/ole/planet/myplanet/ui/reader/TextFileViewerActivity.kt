package org.ole.planet.myplanet.ui.reader

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
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils
import org.ole.planet.myplanet.utilities.FileUtils

class TextFileViewerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTextfileViewerBinding
    private var fileName: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTextfileViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtils.setupEdgeToEdge(this, binding.root)
        renderTextFile()
    }

    private fun renderTextFile() {
        val textFileOpenIntent = intent
        fileName = textFileOpenIntent.getStringExtra("TOUCHED_FILE")
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
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val basePath = getExternalFilesDir(null)
                val file = File(basePath, "ole/$fileName")
                val text = file.readText()
                withContext(Dispatchers.Main) {
                    binding.textFileContent.text = text
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
