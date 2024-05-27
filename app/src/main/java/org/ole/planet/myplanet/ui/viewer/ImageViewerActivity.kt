package org.ole.planet.myplanet.ui.viewer

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import org.ole.planet.myplanet.databinding.ActivityImageViewerBinding
import java.io.File
import java.util.regex.Pattern

class ImageViewerActivity : AppCompatActivity() {
    private lateinit var activityImageViewerBinding: ActivityImageViewerBinding
    var fileName: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityImageViewerBinding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(activityImageViewerBinding.root)
        renderImageFile()
    }

    private fun renderImageFile() {
        val isFullPath = intent.getBooleanExtra("isFullPath", false)
        val imageOpenIntent = intent
        fileName = imageOpenIntent.getStringExtra("TOUCHED_FILE")
        if (fileName != null && fileName?.isNotEmpty() == true) {
            val regex = Regex(".+/([^/]+\\.(jpg|jpeg|png|gif|bmp))")

            val matchResult = regex.find(fileName ?: "")
            val nameWithExtension = matchResult?.groupValues?.get(1)
            val nameWithoutExtension = nameWithExtension?.substringBefore(".")
            activityImageViewerBinding.imageFileName.text = nameWithoutExtension
            activityImageViewerBinding.imageFileName.visibility = View.VISIBLE
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
                Glide.with(applicationContext).load(imageFile).into(activityImageViewerBinding.imageViewer)
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
        val requestOptions = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
        Glide.with(this)
            .load(fileName)
            .apply(requestOptions)
            .into(activityImageViewerBinding.imageViewer)
    }
}
