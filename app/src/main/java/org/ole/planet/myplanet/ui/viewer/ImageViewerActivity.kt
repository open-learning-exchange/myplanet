package org.ole.planet.myplanet.ui.viewer

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import java.io.File
import java.util.regex.Pattern
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityImageViewerBinding
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils
import org.ole.planet.myplanet.utilities.FileUtils

class ImageViewerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityImageViewerBinding
    var fileName: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtils.setupEdgeToEdge(this, binding.root)
        renderImageFile()
    }

    private fun renderImageFile() {
        val isFullPath = intent.getBooleanExtra("isFullPath", false)
        val imageOpenIntent = intent
        fileName = imageOpenIntent.getStringExtra("TOUCHED_FILE")
        if (!fileName.isNullOrEmpty()) {
            binding.imageFileName.text = FileUtils.nameWithoutExtension(fileName)
            binding.imageFileName.visibility = View.VISIBLE
        } else {
            binding.imageFileName.text = getString(R.string.message_placeholder, "No file selected")
            binding.imageFileName.visibility = View.VISIBLE
        }

        if (fileName?.matches(".*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}.*".toRegex()) == true) {
            displayCapturedImage()
        } else {
            try {
                val imageFile: File? = if (isFullPath) {
                    fileName?.let { File(it) }
                } else {
                    val basePath = getExternalFilesDir(null)
                    File(basePath, "ole/$fileName")
                }
                Glide.with(applicationContext)
                    .load(imageFile)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .fitCenter()
                    .placeholder(R.drawable.ole_logo)
                    .error(R.drawable.ole_logo)
                    .into(binding.imageViewer)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun displayCapturedImage() {
        val uuidPattern = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/")
        val matcher = fileName?.let { uuidPattern.matcher(it) }
        if (matcher != null) {
            if (matcher.find()) {
                fileName = fileName?.substring(matcher.group().length)
            }
        }
        Glide.with(this)
            .load(fileName)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .skipMemoryCache(true)
            .fitCenter()
            .placeholder(R.drawable.ole_logo)
            .error(R.drawable.ole_logo)
            .into(binding.imageViewer)
    }
}
