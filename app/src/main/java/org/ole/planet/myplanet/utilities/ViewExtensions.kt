package org.ole.planet.myplanet.utilities

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.Toast
import com.bumptech.glide.Glide
import org.ole.planet.myplanet.R

fun Context.toast(s: String?, duration: Int = Toast.LENGTH_LONG) {
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(this, s, duration).show()
    }
}

fun ImageView.loadImage(userImage: String?) {
    if (!userImage.isNullOrEmpty()) {
        Glide.with(this.context)
            .load(userImage)
            .placeholder(R.drawable.profile)
            .error(R.drawable.profile)
            .into(this)
    } else {
        this.setImageResource(R.drawable.ole_logo)
    }
}
