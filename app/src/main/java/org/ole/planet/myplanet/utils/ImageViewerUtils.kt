package org.ole.planet.myplanet.utils

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import androidx.core.graphics.drawable.toDrawable
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.io.File
import java.util.Locale
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.DialogZoomableImageBinding

object ImageViewerUtils {
    private var activeDialog: Dialog? = null

    fun showZoomableImage(context: Context, imagePath: String) {
        activeDialog?.dismiss()

        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        activeDialog = dialog

        val binding = DialogZoomableImageBinding.inflate(LayoutInflater.from(context))

        dialog.setContentView(binding.root)
        dialog.window?.setBackgroundDrawable(Color.BLACK.toDrawable())

        val request = Glide.with(binding.photoView.context)

        val isUrl = imagePath.startsWith("http://", ignoreCase = true) ||
                    imagePath.startsWith("https://", ignoreCase = true)

        val target = if (isUrl) {
            request.load(imagePath)
        } else {
            val file = File(imagePath)
            if (imagePath.lowercase(Locale.getDefault()).endsWith(".gif")) {
                request.asGif().load(file).error(request.asGif().load(imagePath))
            } else {
                request.load(file).error(request.load(imagePath))
            }
        }

        target.diskCacheStrategy(DiskCacheStrategy.ALL).fitCenter()
            .error(R.drawable.ic_loading).into(binding.photoView)

        binding.closeButton.setOnClickListener {
            dialog.dismiss()
            activeDialog = null
        }

        dialog.setOnDismissListener {
            activeDialog = null
        }

        dialog.show()
    }
}
