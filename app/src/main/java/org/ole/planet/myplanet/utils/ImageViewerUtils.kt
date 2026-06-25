package org.ole.planet.myplanet.utils

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.core.graphics.drawable.toDrawable
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.github.chrisbanes.photoview.PhotoView
import java.io.File
import java.util.Locale
import org.ole.planet.myplanet.R

object ImageViewerUtils {
    /** Shows a fullscreen, pinch-to-zoom dialog for the image at [imagePath]. */
    fun showZoomableImage(context: Context, imagePath: String) {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_zoomable_image, null)
        val photoView = view.findViewById<PhotoView>(R.id.photoView)
        val closeButton = view.findViewById<ImageView>(R.id.closeButton)

        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(Color.BLACK.toDrawable())

        val request = Glide.with(photoView.context)
        val file = File(imagePath)
        val target = if (imagePath.lowercase(Locale.getDefault()).endsWith(".gif")) {
            request.asGif().load(file).error(request.asGif().load(imagePath))
        } else {
            request.load(file).error(request.load(imagePath))
        }
        target.diskCacheStrategy(DiskCacheStrategy.ALL).fitCenter()
            .error(R.drawable.ic_loading).into(photoView)

        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
