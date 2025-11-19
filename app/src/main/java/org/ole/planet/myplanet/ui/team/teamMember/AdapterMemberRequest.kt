package org.ole.planet.myplanet.ui.team.teamMember

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.databinding.RowMemberRequestBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.DiffUtils

class AdapterMemberRequest(
    private val onAction: (RealmUserModel, Boolean) -> Unit
) : ListAdapter<RealmUserModel, AdapterMemberRequest.ViewHolderUser>(
    DiffUtils.itemCallback(
        areItemsTheSame = { old, new -> old.id == new.id },
        areContentsTheSame = { old, new -> old == new }
    )
) {
    private var teamUiInfo: TeamUiInfo? = null

    fun setTeamUiInfo(teamUiInfo: TeamUiInfo) {
        this.teamUiInfo = teamUiInfo
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderUser {
        val binding = RowMemberRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderUser(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderUser, position: Int) {
        val user = getItem(position)
        val binding = holder.binding
        binding.tvName.text = user.name

        teamUiInfo?.let {
            val isRequester = user.id == it.currentUserId
            val isGuest = it.currentUserId?.startsWith("guest") == true
            val canAccept = it.memberCount < 12 && it.canModerate && !isGuest && !isRequester

            with(binding) {
                btnAccept.isEnabled = canAccept
                btnReject.isEnabled = it.canModerate && !isGuest && !isRequester

                btnAccept.setOnClickListener { if (canAccept) onAction(user, true) }
                btnReject.setOnClickListener { if (btnReject.isEnabled) onAction(user, false) }
            }
        }
    }

    class ViewHolderUser(val binding: RowMemberRequestBinding) : RecyclerView.ViewHolder(binding.root)
}