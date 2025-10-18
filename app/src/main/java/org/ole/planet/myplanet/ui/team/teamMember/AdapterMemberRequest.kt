package org.ole.planet.myplanet.ui.team.teamMember

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.MemberChangeListener
import org.ole.planet.myplanet.databinding.RowMemberRequestBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getJoinedMember
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.utilities.Utilities

class AdapterMemberRequest(
    private val context: Context,
    private val list: MutableList<RealmUserModel>,
    private val mRealm: Realm,
    private val currentUser: RealmUserModel,
    private val listener: MemberChangeListener,
    private val teamRepository: TeamRepository,
) : RecyclerView.Adapter<AdapterMemberRequest.ViewHolderUser>() {
    private lateinit var rowMemberRequestBinding: RowMemberRequestBinding
    private var teamId: String? = null
    private lateinit var team: RealmMyTeam
    private var cachedModerationStatus: Boolean? = null

    fun setTeamId(teamId: String?) {
        this.teamId = teamId
        cachedModerationStatus = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderUser {
        rowMemberRequestBinding = RowMemberRequestBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderUser(rowMemberRequestBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderUser, position: Int) {
        val currentItem = list.getOrNull(position) ?: return
        rowMemberRequestBinding.tvName.text = currentItem.name ?: currentItem.toString()

        team = try {
            mRealm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
                ?: throw IllegalArgumentException("Team not found for ID: $teamId")
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            try {
                mRealm.where(RealmMyTeam::class.java).equalTo("teamId", teamId).findFirst()
                    ?: throw IllegalArgumentException("Team not found for ID: $teamId")
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
                return
            }
        }

        with(rowMemberRequestBinding) {
            val members = getJoinedMember("$teamId", mRealm).size
            val userCanModerateRequests = canModerateRequests()
            val isRequester = currentItem.id == currentUser.id
            btnAccept.isEnabled = members < 12
            btnReject.isEnabled = true
            btnAccept.setOnClickListener(null)
            btnReject.setOnClickListener(null)

            if (isRequester) {
                btnAccept.isEnabled = false
                btnReject.isEnabled = true
                btnReject.setOnClickListener {
                    val adapterPosition = holder.bindingAdapterPosition
                    if (adapterPosition != RecyclerView.NO_POSITION && adapterPosition < list.size) {
                        acceptReject(currentItem, false, adapterPosition)
                    }
                }
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

    private fun canModerateRequests(): Boolean {
        cachedModerationStatus?.let { return it }

        val teamId = this.teamId
        val userId = currentUser.id
        if (teamId.isNullOrBlank() || userId.isNullOrBlank()) {
            cachedModerationStatus = false
            return false
        }

        val membershipRecord = mRealm.where(RealmMyTeam::class.java)
            .equalTo("teamId", teamId)
            .equalTo("docType", "membership")
            .equalTo("userId", userId)
            .findFirst()

        val canModerate = membershipRecord?.let { it.isLeader || it.docType == "membership" } ?: false
        cachedModerationStatus = canModerate
        return canModerate
    }


    private fun handleClick(holder: RecyclerView.ViewHolder, isAccepted: Boolean) {
        val adapterPosition = holder.bindingAdapterPosition
        if (adapterPosition != RecyclerView.NO_POSITION && adapterPosition < list.size) {
            val targetUser = list[adapterPosition]
            acceptReject(targetUser, isAccepted, adapterPosition)
        }
    }

    private fun acceptReject(userModel: RealmUserModel, isAccept: Boolean, position: Int) {
        val userId = userModel.id
        val teamId = this.teamId

        if (teamId.isNullOrBlank() || userId.isNullOrBlank()) {
            Utilities.toast(context, context.getString(R.string.request_failed_please_retry))
            return
        }

        list.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, list.size)

        MainApplication.applicationScope.launch {
            val result = teamRepository.respondToMemberRequest(teamId, userId, isAccept)
            if (result.isSuccess) {
                runCatching { teamRepository.syncTeamActivities(context) }
                    .onFailure { it.printStackTrace() }
                withContext(Dispatchers.Main) {
                    listener.onMemberChanged()
                }
            } else {
                withContext(Dispatchers.Main) {
                    list.add(position, userModel)
                    notifyItemInserted(position)
                    Utilities.toast(context, context.getString(R.string.request_failed_please_retry))
                    listener.onMemberChanged()
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolderUser(rowMemberRequestBinding: RowMemberRequestBinding) : RecyclerView.ViewHolder(rowMemberRequestBinding.root)
}
