package org.ole.planet.myplanet.ui.voices

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.io.File
import java.util.Locale
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemImageBinding
import org.ole.planet.myplanet.utilities.DiffUtils

class ImageAdapter(
    private val onImageClick: (String) -> Unit
) : ListAdapter<String, ImageAdapter.ImageViewHolder>(
    DiffUtils.itemCallback(
        areItemsTheSame = { old, new -> old == new },
        areContentsTheSame = { old, new -> old == new }
    )
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ImageViewHolder(private val binding: ItemImageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(imageUrl: String) {
            loadImage(binding.imageView, imageUrl)
            binding.root.setOnClickListener { onImageClick(imageUrl) }
        }

        private fun loadImage(imageView: ImageView, path: String) {
            val request = Glide.with(imageView.context)
            val file = File(path)
            val target = if (path.lowercase(Locale.getDefault()).endsWith(".gif")) {
                request.asGif().load(file).error(request.asGif().load(path))
            } else {
                request.load(file).error(request.load(path))
            }
            target.diskCacheStrategy(DiskCacheStrategy.ALL)
                .fitCenter()
                .placeholder(R.drawable.ic_loading)
                .error(R.drawable.ic_loading)
                .into(imageView)
        }
    }
}
