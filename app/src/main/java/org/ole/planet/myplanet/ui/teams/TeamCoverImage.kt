package org.ole.planet.myplanet.ui.teams

import android.content.Context
import android.widget.ImageView
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.signature.ObjectKey
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.utils.UrlUtils

object TeamCoverImage {
    fun model(context: Context, teamId: String?, imageName: String?, rev: String?): Pair<Any, String>? {
        val localFile = MyTeam.getAttachmentFile(context, teamId, imageName)
        if (localFile?.exists() == true) return localFile to "${localFile.lastModified()}"
        val url = UrlUtils.getTeamImageUrl(teamId, imageName) ?: return null
        val glideUrl = GlideUrl(url, LazyHeaders.Builder().addHeader("Authorization", UrlUtils.header).build())
        return glideUrl to "${rev.orEmpty()}:$imageName"
    }

    fun load(view: ImageView, teamId: String?, imageName: String?, rev: String?) {
        val cover = model(view.context, teamId, imageName, rev)
        if (cover == null) {
            Glide.with(view).clear(view)
            view.isVisible = false
            return
        }
        val (model, signature) = cover
        view.isVisible = true
        Glide.with(view)
            .load(model)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .signature(ObjectKey(signature))
            .error(R.drawable.ole_logo)
            .into(view)
    }
}
