package org.ole.planet.myplanet.utilities

import android.widget.ImageView
import com.bumptech.glide.Glide
import org.ole.planet.myplanet.R

object ImageUtils {
    fun loadImage(userImage: String?, imageView: ImageView) {
        val sanitizedUrl = UrlUtils.sanitizeUserImageUrl(userImage)
        if (!sanitizedUrl.isNullOrEmpty()) {
            Glide.with(imageView.context)
                .load(sanitizedUrl)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(imageView)
        } else {
            imageView.setImageResource(R.drawable.ole_logo)
        }
    }
}
