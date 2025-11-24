package org.ole.planet.myplanet.ui.news

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.github.chrisbanes.photoview.PhotoView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.NewsImage
import java.io.File

class NewsImageAdapter(private val images: List<NewsImage>) : RecyclerView.Adapter<NewsImageAdapter.ImageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_news_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val image = images[position]
        holder.bind(image)
    }

    override fun getItemCount(): Int = images.size

    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.img_news_item)

        fun bind(image: NewsImage) {
            val context = itemView.context
            val path = image.path
            if (path == null) return

            val request = Glide.with(context)
            val file = File(path)
            val loadObj = if (file.exists()) file else path

            val target = if (image.isGif) {
                request.asGif().load(loadObj)
            } else {
                request.load(loadObj)
            }

            target.diskCacheStrategy(DiskCacheStrategy.ALL)
                .fitCenter()
                .placeholder(R.drawable.ic_loading)
                .error(R.drawable.ic_loading)
                .into(imageView)

            imageView.setOnClickListener {
                showZoomableImage(context, path, image.isGif)
            }
        }

        private fun showZoomableImage(context: android.content.Context, imageUrl: String, isGif: Boolean) {
            val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_zoomable_image, null)
            val photoView = view.findViewById<PhotoView>(R.id.photoView)
            val closeButton = view.findViewById<ImageView>(R.id.closeButton)

            dialog.setContentView(view)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))

            val request = Glide.with(photoView.context)
            val file = File(imageUrl)
            val loadObj = if (file.exists()) file else imageUrl

            val target = if (isGif) {
                request.asGif().load(loadObj)
            } else {
                request.load(loadObj)
            }
            target.diskCacheStrategy(DiskCacheStrategy.ALL).fitCenter().error(R.drawable.ic_loading).into(photoView)

            closeButton.setOnClickListener { dialog.dismiss() }

            dialog.show()
        }
    }
}
