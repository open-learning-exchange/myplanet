package org.ole.planet.myplanet.ui.teams

import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AlertCreateTeamBinding
import org.ole.planet.myplanet.utils.AttachmentReader
import org.ole.planet.myplanet.utils.UriAttachment

class TeamCoverPicker(fragment: Fragment) {
    var selectedUri: Uri? = null
        private set
    private var binding: AlertCreateTeamBinding? = null

    private val launcher = fragment.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        restore(uri)
    }

    fun bind(binding: AlertCreateTeamBinding, teamId: String?, imageName: String?, rev: String?) {
        this.binding = binding
        selectedUri = null
        TeamCoverImage.load(binding.ivTeamCover, teamId, imageName, rev)
        binding.btnTeamCover.setText(
            if (imageName.isNullOrBlank()) R.string.add_cover_image else R.string.change_cover_image
        )
        binding.btnTeamCover.setOnClickListener { launcher.launch("image/*") }
    }

    fun restore(uri: Uri) {
        selectedUri = uri
        binding?.let { b ->
            b.ivTeamCover.isVisible = true
            Glide.with(b.ivTeamCover).load(uri).into(b.ivTeamCover)
            b.btnTeamCover.setText(R.string.change_cover_image)
        }
    }

    suspend fun read(reader: AttachmentReader): UriAttachment? {
        val uri = selectedUri ?: return null
        return reader.read(uri).takeIf { !it.name.isNullOrBlank() && it.bytes != null }
    }

    fun unbind() {
        binding = null
        selectedUri = null
    }
}
