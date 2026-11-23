package org.ole.planet.myplanet.ui.teams

import android.content.Context
import android.net.Uri
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.signature.ObjectKey
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AlertCreateTeamBinding
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.utils.AttachmentReader
import org.ole.planet.myplanet.utils.UriAttachment
import org.ole.planet.myplanet.utils.UrlUtils

object TeamCover {
    /**
     * The Glide model for a team's cover and a cache signature for it, or null when the team has
     * no cover. Prefers the copy saved on this device (a cover picked here that may not be
     * uploaded yet) and falls back to the server's attachment.
     */
    fun model(context: Context, teamId: String?, imageName: String?, rev: String?): Pair<Any, String>? {
        val localFile = MyTeam.getAttachmentFile(context, teamId, imageName)
        if (localFile?.exists() == true) return localFile to "${localFile.lastModified()}"
        val url = UrlUtils.getTeamImageUrl(teamId, imageName) ?: return null
        val glideUrl = GlideUrl(url, LazyHeaders.Builder().addHeader("Authorization", UrlUtils.header).build())
        return glideUrl to "${rev.orEmpty()}:$imageName"
    }

    /** Shows a team's cover in [view], hiding the view when the team has no cover. */
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

/**
 * Cover-image picking for the create/edit team dialog. Construct it as a fragment property so the
 * activity-result launcher is registered before the fragment starts.
 */
class TeamCoverPicker(fragment: Fragment) {
    private var selectedUri: Uri? = null
    private var binding: AlertCreateTeamBinding? = null

    private val launcher = fragment.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        selectedUri = uri
        binding?.let { b ->
            b.ivTeamCover.isVisible = true
            Glide.with(b.ivTeamCover).load(uri).into(b.ivTeamCover)
            b.btnTeamCover.setText(R.string.change_cover_image)
        }
    }

    fun bind(binding: AlertCreateTeamBinding, teamId: String?, imageName: String?, rev: String?) {
        this.binding = binding
        selectedUri = null
        TeamCover.load(binding.ivTeamCover, teamId, imageName, rev)
        binding.btnTeamCover.setText(
            if (imageName.isNullOrBlank()) R.string.add_cover_image else R.string.change_cover_image
        )
        binding.btnTeamCover.setOnClickListener { launcher.launch("image/*") }
    }

    /** The newly picked image, or null when the user kept the existing cover (or has none). */
    suspend fun read(reader: AttachmentReader): UriAttachment? {
        val uri = selectedUri ?: return null
        return reader.read(uri).takeIf { !it.name.isNullOrBlank() && it.bytes != null }
    }

    fun unbind() {
        binding = null
        selectedUri = null
    }
}
