package org.ole.planet.myplanet.ui.reader

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityMarkdownViewerBinding
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText

class MarkdownViewerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMarkdownViewerBinding
    private var fileName: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMarkdownViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtils.setupEdgeToEdge(this, binding.root)
        renderMarkdownFile()
    }

    private fun renderMarkdownFile() {
        val markdownOpenIntent = intent
        fileName = markdownOpenIntent.getStringExtra("TOUCHED_FILE")
        if (!fileName.isNullOrEmpty()) {

            binding.markdownFileName.text = FileUtils.nameWithoutExtension(fileName)
            binding.markdownFileName.visibility = View.VISIBLE
        } else {
            binding.markdownFileName.text = getString(R.string.message_placeholder, "No file selected")
            binding.markdownFileName.visibility = View.VISIBLE
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val basePath = getExternalFilesDir(null)
                val markdownFile = File(basePath, "ole/$fileName")
                if (markdownFile.exists()) {
                    val markdownContent = readMarkdownFile(markdownFile)
                    withContext(Dispatchers.Main) {
                        setMarkdownText(binding.markdownView, markdownContent)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MarkdownViewerActivity,
                            getString(R.string.unable_to_load) + fileName,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Throws(IOException::class)
    private fun readMarkdownFile(file: File): String {
        return file.useLines { sequence ->
            sequence.joinToString("\n")
        }
    }
}
