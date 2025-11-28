package org.ole.planet.myplanet.ui.team.teamMember

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.MemberChangeListener
import org.ole.planet.myplanet.databinding.RowMemberRequestBinding
import org.ole.planet.myplanet.model.MemberRequest
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.utilities.Utilities

class AdapterMemberRequest(
    private val context: Context,
    private val listener: MemberChangeListener,
    private val teamRepository: TeamRepository,
    private val teamId: String,
) : ListAdapter<MemberRequest, AdapterMemberRequest.ViewHolderUser>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderUser {
        val binding = RowMemberRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderUser(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderUser, position: Int) {
        val currentItem = getItem(position)
        val binding = holder.binding
        binding.tvName.text = currentItem.userName

        with(binding) {
            val userCanModerateRequests = currentItem.canModerate
            btnAccept.isEnabled = currentItem.teamMemberCount < 12
            btnReject.isEnabled = true
            btnAccept.setOnClickListener(null)
            btnReject.setOnClickListener(null)

            if (currentItem.isCurrentUser) {
                btnAccept.isEnabled = false
                btnReject.isEnabled = false
            } else if (!userCanModerateRequests) {
                btnAccept.isEnabled = false
                btnReject.isEnabled = false
            } else {
                btnAccept.setOnClickListener { handleClick(holder, true) }
                btnReject.setOnClickListener { handleClick(holder, false) }
            }
        }
    }

    private fun handleClick(holder: RecyclerView.ViewHolder, isAccepted: Boolean) {
        val adapterPosition = holder.bindingAdapterPosition
        if (adapterPosition != RecyclerView.NO_POSITION) {
            val targetUser = getItem(adapterPosition)
            if (targetUser.isCurrentUser) return
            acceptReject(targetUser, isAccepted)
        }
    }

    private fun acceptReject(userModel: MemberRequest, isAccept: Boolean) {
        val userId = userModel.userId
        if (teamId.isBlank()) {
            Utilities.toast(context, context.getString(R.string.request_failed_please_retry))
            return
        }

        MainApplication.applicationScope.launch {
            val result = teamRepository.respondToMemberRequest(teamId, userId, isAccept)
            if (result.isSuccess) {
                runCatching { teamRepository.syncTeamActivities() }
                    .onFailure { it.printStackTrace() }
                withContext(Dispatchers.Main) {
                    listener.onMemberChanged()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Utilities.toast(context, context.getString(R.string.request_failed_please_retry))
                    listener.onMemberChanged()
                }
            }
        }
    }

    class ViewHolderUser(val binding: RowMemberRequestBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<MemberRequest>() {
            override fun areItemsTheSame(oldItem: MemberRequest, newItem: MemberRequest): Boolean {
                return oldItem.userId == newItem.userId
            }

            override fun areContentsTheSame(oldItem: MemberRequest, newItem: MemberRequest): Boolean {
                return oldItem == newItem
            }
        }
    }
}
