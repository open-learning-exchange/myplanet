package org.ole.planet.myplanet.ui.team.teamMember

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.databinding.RowMemberRequestBinding

typealias OnRequestAction = (userId: String, isAccepted: Boolean) -> Unit

class AdapterMemberRequest(
    private val onRequestAction: OnRequestAction
) : ListAdapter<MemberRequest, AdapterMemberRequest.ViewHolderUser>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<MemberRequest>() {
            override fun areItemsTheSame(oldItem: MemberRequest, newItem: MemberRequest): Boolean {
                return oldItem.user.id == newItem.user.id
            }

            override fun areContentsTheSame(
                oldItem: MemberRequest, newItem: MemberRequest
            ): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderUser {
        val binding =
            RowMemberRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderUser(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderUser, position: Int) {
        val memberRequest = getItem(position)
        val binding = holder.binding
        binding.tvName.text = memberRequest.user.name

        val canPerformAction =
            memberRequest.canModerate && !memberRequest.isUserLoggedIn

        with(binding) {
            btnAccept.isEnabled = canPerformAction && memberRequest.memberCount < 12
            btnReject.isEnabled = canPerformAction

            if (canPerformAction) {
                btnAccept.setOnClickListener {
                    memberRequest.user.id?.let { userId ->
                        onRequestAction(userId, true)
                    }
                }
                btnReject.setOnClickListener {
                    memberRequest.user.id?.let { userId ->
                        onRequestAction(userId, false)
                    }
                }
            } else {
                btnAccept.setOnClickListener(null)
                btnReject.setOnClickListener(null)
            }
        }
    }

    class ViewHolderUser(val binding: RowMemberRequestBinding) :
        RecyclerView.ViewHolder(binding.root)
}
