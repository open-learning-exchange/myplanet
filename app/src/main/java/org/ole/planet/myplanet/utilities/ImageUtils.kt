package org.ole.planet.myplanet.utilities

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.ole.planet.myplanet.R

object ImageUtils {
    fun loadImage(userImage: String?, imageView: ImageView) {
        if (!userImage.isNullOrEmpty()) {
            Glide.with(imageView.context)
                .load(userImage)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .circleCrop()
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(imageView)
        } else {
            imageView.setImageResource(R.drawable.ole_logo)
        }
    }
}
