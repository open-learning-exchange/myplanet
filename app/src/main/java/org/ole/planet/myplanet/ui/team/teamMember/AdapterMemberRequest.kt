package org.ole.planet.myplanet.ui.team.teamMember

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.databinding.RowMemberRequestBinding
import org.ole.planet.myplanet.model.RealmUserModel

class AdapterMemberRequest(
    private val context: Context,
    private val list: MutableList<RealmUserModel>,
    private val teamId: String?,
    private val currentUser: RealmUserModel,
    private val isTeamLeader: Boolean,
    private val members: Int,
    private val viewModel: MemberRequestViewModel
) : RecyclerView.Adapter<AdapterMemberRequest.ViewHolderUser>() {

    private lateinit var rowMemberRequestBinding: RowMemberRequestBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderUser {
        rowMemberRequestBinding = RowMemberRequestBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderUser(rowMemberRequestBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderUser, position: Int) {
        val currentItem = list.getOrNull(position) ?: return
        rowMemberRequestBinding.tvName.text = currentItem.name ?: currentItem.toString()

        with(rowMemberRequestBinding) {
            if (members >= 12) {
                btnAccept.isEnabled = false
            }
            if (isGuestUser() || !isTeamLeader) {
                btnReject.isEnabled = false
                btnAccept.isEnabled = false
            }
            btnAccept.setOnClickListener { handleClick(holder, true) }
            btnReject.setOnClickListener { handleClick(holder, false) }
        }
    }

    private fun isGuestUser() = currentUser.id?.startsWith("guest") == true

    private fun handleClick(holder: RecyclerView.ViewHolder, isAccepted: Boolean) {
        val position = holder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION && position < list.size) {
            val userModel = list[position]
            val userId = userModel.id
            list.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, list.size)
            if (teamId != null && userId != null) {
                if (isAccepted) {
                    viewModel.acceptRequest(teamId, userId)
                } else {
                    viewModel.rejectRequest(teamId, userId)
                }
            }
        }
    }

    override fun getItemCount(): Int = list.size

    class ViewHolderUser(binding: RowMemberRequestBinding) : RecyclerView.ViewHolder(binding.root)
}

