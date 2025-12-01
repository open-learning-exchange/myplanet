package org.ole.planet.myplanet.ui.team.teamMember

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.MemberChangeListener
import org.ole.planet.myplanet.databinding.RowMemberRequestBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.Utilities

import androidx.recyclerview.widget.ListAdapter

class AdapterMemberRequest(
    private val context: Context,
    private val currentUser: RealmUserModel,
    private val listener: MemberChangeListener,
    private val teamRepository: TeamRepository,
) : ListAdapter<RealmUserModel, AdapterMemberRequest.ViewHolderUser>(MWC_DIFF_CALLBACK) {
    companion object {
        val MWC_DIFF_CALLBACK = DiffUtils.itemCallback<RealmUserModel>(
            areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
            areContentsTheSame = { oldItem, newItem -> oldItem.name == newItem.name }
        )
    }
    private var teamId: String? = null
    private var teamLeader = false
    private var joinedTeamMembers = 0

    fun setTeamId(teamId: String?) {
        this.teamId = teamId
    }

    fun setData(members: List<RealmUserModel>, isLeader: Boolean, memberCount: Int) {
        teamLeader = isLeader
        joinedTeamMembers = memberCount
        submitList(members)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderUser {
        val binding = RowMemberRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderUser(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderUser, position: Int) {
        val currentItem = getItem(position)
        val binding = holder.binding
        binding.tvName.text = currentItem.name ?: currentItem.toString()

        with(binding) {
            val userCanModerateRequests = teamLeader
            val isRequester = currentItem.id == currentUser.id
            btnAccept.isEnabled = joinedTeamMembers < 12
            btnReject.isEnabled = true
            btnAccept.setOnClickListener(null)
            btnReject.setOnClickListener(null)

            if (isRequester) {
                btnAccept.isEnabled = false
                btnReject.isEnabled = false
                btnAccept.setOnClickListener(null)
                btnReject.setOnClickListener(null)
            } else if (isGuestUser() || !userCanModerateRequests) {
                btnAccept.isEnabled = false
                btnReject.isEnabled = false
            } else {
                btnAccept.setOnClickListener { handleClick(holder, true) }
                btnReject.setOnClickListener { handleClick(holder, false) }
            }
        }
    }

    private fun isGuestUser() = currentUser.id?.startsWith("guest") == true
    private fun handleClick(holder: RecyclerView.ViewHolder, isAccepted: Boolean) {
        val adapterPosition = holder.bindingAdapterPosition
        if (adapterPosition != RecyclerView.NO_POSITION) {
            val targetUser = getItem(adapterPosition)
            if (targetUser.id == currentUser.id) return
            acceptReject(targetUser, isAccepted)
        }
    }

    private fun acceptReject(userModel: RealmUserModel, isAccept: Boolean) {
        val userId = userModel.id
        val teamId = this.teamId

        if (teamId.isNullOrBlank() || userId.isNullOrBlank()) {
            Utilities.toast(context, context.getString(R.string.request_failed_please_retry))
            return
        }

        val originalList = currentList.toList()
        val updatedList = originalList.toMutableList().apply { remove(userModel) }
        submitList(updatedList)

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
                    submitList(originalList)
                    Utilities.toast(context, context.getString(R.string.request_failed_please_retry))
                    listener.onMemberChanged()
                }
            }
        }
    }

    class ViewHolderUser(val binding: RowMemberRequestBinding) : RecyclerView.ViewHolder(binding.root)
}
