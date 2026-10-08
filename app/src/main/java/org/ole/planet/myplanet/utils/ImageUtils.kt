package org.ole.planet.myplanet.utils

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.ole.planet.myplanet.R

object ImageUtils {
    fun loadProfileImage(image: String?, imageView: ImageView, sizePx: Int) {
        if (image.isNullOrEmpty()) {
            imageView.setImageResource(R.drawable.profile)
            return
        }
        Glide.with(imageView.context)
            .load(image)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .override(sizePx, sizePx)
            .circleCrop()
            .placeholder(R.drawable.profile)
            .error(R.drawable.profile)
            .into(imageView)
    }

    fun loadImage(userImage: String?, imageView: ImageView, sizePx: Int? = null) {
        if (!userImage.isNullOrEmpty()) {
            Glide.with(imageView.context)
                .load(userImage)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .apply {
                    if (sizePx != null) {
                        override(sizePx, sizePx)
                    }
                }
                .circleCrop()
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(imageView)
        } else {
            imageView.setImageResource(R.drawable.ole_logo)
        }
    }

    fun loadPlaceholderImage(image: String?, imageView: ImageView, sizePx: Int? = null) {
        Glide.with(imageView.context)
            .load(image)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .apply {
                if (sizePx != null) {
                    override(sizePx, sizePx)
                }
            }
            .placeholder(R.drawable.profile)
            .error(R.drawable.profile)
            .into(imageView)
    }
}
