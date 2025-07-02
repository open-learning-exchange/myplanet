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
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowNewsBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import java.io.File

class NewsImageLoader(private val context: Context, private val realm: Realm) {
    private var currentZoomDialog: Dialog? = null

    fun loadImage(binding: RowNewsBinding, news: RealmNews?) {
        val imageUrls = news?.imageUrls
        if (imageUrls != null && imageUrls.isNotEmpty()) {
            try {
                val imgObject = Gson().fromJson(imageUrls[0], JsonObject::class.java)
                binding.imgNews.visibility = View.VISIBLE
                Glide.with(context).load(File(getString("imageUrl", imgObject)))
                    .into(binding.imgNews)

                binding.imgNews.setOnClickListener {
                    showZoomableImage(it.context, getString("imageUrl", imgObject))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            loadRemoteImage(binding, news)
        }
    }

    private fun loadRemoteImage(binding: RowNewsBinding, news: RealmNews?) {
        news?.imagesArray?.let { imagesArray ->
            if (imagesArray.size() > 0) {
                val ob = imagesArray[0]?.asJsonObject
                getString("resourceId", ob).let { resourceId ->
                    realm.where(RealmMyLibrary::class.java).equalTo("_id", resourceId).findFirst()?.let { library ->
                        context.getExternalFilesDir(null)?.let { basePath ->
                            val imageFile = File(basePath, "ole/${library.id}/${library.resourceLocalAddress}")
                            if (imageFile.exists()) {
                                Glide.with(context)
                                    .load(imageFile)
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
            }
        }
        binding.imgNews.visibility = View.GONE
    }

    private fun showZoomableImage(context: Context, imageUrl: String) {
        currentZoomDialog?.dismiss()

        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        currentZoomDialog = dialog

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_zoomable_image, null)
        val photoView = view.findViewById<PhotoView>(R.id.photoView)
        val closeButton = view.findViewById<ImageView>(R.id.closeButton)

        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(Color.BLACK.toDrawable())

        Glide.with(context)
            .load(imageUrl)
            .error(R.drawable.ic_loading)
            .into(photoView)

        closeButton.setOnClickListener {
            dialog.dismiss()
            currentZoomDialog = null
        }

        dialog.setOnDismissListener {
            currentZoomDialog = null
        }

        dialog.show()
    }
}

