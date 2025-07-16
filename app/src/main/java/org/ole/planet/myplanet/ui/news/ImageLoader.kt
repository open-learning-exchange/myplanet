package org.ole.planet.myplanet.ui.news

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import androidx.core.graphics.drawable.toDrawable
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import io.realm.Realm
import java.io.File
import java.util.Locale
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowNewsBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.utilities.JsonUtils

class ImageLoader(private val context: Context, private val realm: Realm) {
    fun loadImage(binding: RowNewsBinding, news: RealmNews?) {
        val imageUrls = news?.imageUrls
        if (!imageUrls.isNullOrEmpty()) {
            try {
                val imgObject = com.google.gson.Gson().fromJson(imageUrls[0], com.google.gson.JsonObject::class.java)
                val path = JsonUtils.getString("imageUrl", imgObject)
                val request = Glide.with(context)
                val target = if (path.lowercase(Locale.getDefault()).endsWith(".gif")) {
                    request.asGif().load(if (File(path).exists()) File(path) else path)
                } else {
                    request.load(if (File(path).exists()) File(path) else path)
                }
                target.placeholder(R.drawable.ic_loading)
                    .error(R.drawable.ic_loading)
                    .into(binding.imgNews)
                binding.imgNews.visibility = View.VISIBLE
                binding.imgNews.setOnClickListener {
                    showZoomableImage(it.context, path)
                }
                return
            } catch (_: Exception) {
            }
        }

        news?.imagesArray?.let { imagesArray ->
            if (imagesArray.size() > 0) {
                val ob = imagesArray[0]?.asJsonObject
                val resourceId = JsonUtils.getString("resourceId", ob)
                val library = realm.where(RealmMyLibrary::class.java)
                    .equalTo("_id", resourceId)
                    .findFirst()
                val basePath = context.getExternalFilesDir(null)
                if (library != null && basePath != null) {
                    val imageFile = File(basePath, "ole/${'$'}{library.id}/${'$'}{library.resourceLocalAddress}")
                    if (imageFile.exists()) {
                        val request = Glide.with(context)
                        val isGif = library.resourceLocalAddress?.lowercase(Locale.getDefault())?.endsWith(".gif") == true
                        val target = if (isGif) {
                            request.asGif().load(imageFile)
                        } else {
                            request.load(imageFile)
                        }
                        target.placeholder(R.drawable.ic_loading)
                            .error(R.drawable.ic_loading)
                            .into(binding.imgNews)
                        binding.imgNews.visibility = View.VISIBLE
                        binding.imgNews.setOnClickListener {
                            showZoomableImage(it.context, imageFile.toString())
                        }
                        return
                    }
                }
            }
        }
        binding.imgNews.visibility = View.GONE
    }

    fun showZoomableImage(context: Context, imageUrl: String) {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_zoomable_image, null)
        val photoView = view.findViewById<PhotoView>(R.id.photoView)
        val closeButton = view.findViewById<ImageView>(R.id.closeButton)

        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(Color.BLACK.toDrawable())

        val request = Glide.with(context)
        val target = if (imageUrl.lowercase(Locale.getDefault()).endsWith(".gif")) {
            val file = File(imageUrl)
            if (file.exists()) request.asGif().load(file) else request.asGif().load(imageUrl)
        } else {
            val file = File(imageUrl)
            if (file.exists()) request.load(file) else request.load(imageUrl)
        }
        target.error(R.drawable.ic_loading).into(photoView)

        closeButton.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}

