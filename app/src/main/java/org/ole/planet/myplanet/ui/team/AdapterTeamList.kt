package org.ole.planet.myplanet.ui.team

import android.content.Context
import android.content.DialogInterface
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemTeamListBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.TimeUtils

class AdapterTeamList(
    private val context: Context,
    private val list: List<RealmMyTeam>,
    private val fragmentManager: FragmentManager,
    private val scope: CoroutineScope,
    private val sharedPrefManager: SharedPrefManager
) : ListAdapter<TeamData, AdapterTeamList.ViewHolderTeam>(TeamDiffCallback) {
    private var type: String? = ""
    private var teamListener: OnClickTeamItem? = null
    private var joinRequestListener: OnJoinRequestListener? = null
    private var leaveRequestListener: OnLeaveRequestListener? = null
    private var updateCompleteListener: OnUpdateCompleteListener? = null
    var isGuestUser: Boolean = false
    private val teamStatusCache = mutableMapOf<String, TeamStatus>()
    private val visitCountsCache = mutableMapOf<String, Long>()
    private var visitCounts: Map<String, Long> = emptyMap()
    private var updateListJob: Job? = null
    private var syncJob: Job? = null

    interface OnClickTeamItem {
        fun onEditTeam(team: TeamData?)
    }

    interface OnJoinRequestListener {
        fun onJoinRequest(team: TeamData)
    }

    interface OnLeaveRequestListener {
        fun onLeaveRequest(team: TeamData)
    }

    interface OnUpdateCompleteListener {
        fun onUpdateComplete(itemCount: Int)
    }

    fun setTeamListener(teamListener: OnClickTeamItem?) {
        this.teamListener = teamListener
    }

    fun setJoinRequestListener(joinRequestListener: OnJoinRequestListener) {
        this.joinRequestListener = joinRequestListener
    }

    fun setLeaveRequestListener(leaveRequestListener: OnLeaveRequestListener) {
        this.leaveRequestListener = leaveRequestListener
    }

    fun setUpdateCompleteListener(listener: OnUpdateCompleteListener?) {
        this.updateCompleteListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderTeam {
        val binding = ItemTeamListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderTeam(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderTeam, position: Int) {
        val team = getItem(position)

        with(holder.binding) {
            created.text = TimeUtils.getFormattedDate(team.createdDate ?: 0)
            type.text = team.teamType
            type.visibility = if (team.teamType == null) View.GONE else View.VISIBLE
            name.text = team.name
            noOfVisits.text = context.getString(R.string.number_placeholder, team.visitCount)

            val teamId = team._id.orEmpty()
            val teamStatus = team.teamStatus ?: TeamStatus(
                isMember = false,
                isLeader = false,
                hasPendingRequest = false
            )

            showActionButton(teamStatus.isMember, teamStatus.isLeader, teamStatus.hasPendingRequest, team)

            root.setOnClickListener {
                val activity = context as? AppCompatActivity ?: return@setOnClickListener
                val fragment = TeamDetailFragment.newInstance(
                    teamId = teamId,
                    teamName = "${team.name}",
                    teamType = "${team.type}",
                    isMyTeam = teamStatus.isMember
                )
                NavigationHelper.replaceFragment(
                    activity.supportFragmentManager,
                    R.id.fragment_container,
                    fragment,
                    addToBackStack = true,
                    tag = "TeamDetailFragment"
                )
                sharedPrefManager.setTeamName(team.name)
            }

            btnFeedback.setOnClickListener {
                val feedbackFragment = FeedbackFragment()
                feedbackFragment.show(fragmentManager, "")
                feedbackFragment.arguments = getBundle(team)
            }

            joinLeave.setOnClickListener {
                handleJoinLeaveClick(team)
            }
        }
    }

    private fun ItemTeamListBinding.showActionButton(
        isMyTeam: Boolean,
        isTeamLeader: Boolean,
        hasPendingRequest: Boolean,
        team: TeamData,
    ) {
        if (isMyTeam) {
            name.setTypeface(null, Typeface.BOLD)
        } else {
            name.setTypeface(null, Typeface.NORMAL)
        }
        when {
            isGuestUser -> joinLeave.visibility = View.GONE
            isTeamLeader -> {
                joinLeave.apply {
                    isEnabled = true
                    contentDescription = "${context.getString(R.string.edit)} ${team.name}"
                    visibility = View.VISIBLE
                    setImageResource(R.drawable.ic_edit)
                    clearColorFilter()
                }
            }

            isMyTeam && !isTeamLeader -> {
                joinLeave.apply {
                    isEnabled = true
                    contentDescription = "${context.getString(R.string.leave)} ${team.name}"
                    visibility = View.VISIBLE
                    setImageResource(R.drawable.logout)
                    clearColorFilter()
                }
            }

            !isMyTeam && hasPendingRequest -> {
                joinLeave.apply {
                    isEnabled = false
                    contentDescription = "${context.getString(R.string.requested)} ${team.name}"
                    visibility = View.VISIBLE
                    setImageResource(R.drawable.baseline_hourglass_top_24)
                    setColorFilter("#9fa0a4".toColorInt(), PorterDuff.Mode.SRC_IN)
                }
            }

            !isMyTeam -> {
                joinLeave.apply {
                    isEnabled = true
                    contentDescription = "${context.getString(R.string.request_to_join)} ${team.name}"
                    visibility = View.VISIBLE
                    setImageResource(R.drawable.ic_join_request)
                    clearColorFilter()
                }
            }

            else -> joinLeave.visibility = View.GONE
        }
    }

    private fun handleJoinLeaveClick(team: TeamData) {
        val teamStatus = team.teamStatus ?: TeamStatus(
            isMember = false,
            isLeader = false,
            hasPendingRequest = false
        )

        if (teamStatus.isMember) {
            if (teamStatus.isLeader) {
                teamListener?.onEditTeam(team)
            } else {
                AlertDialog.Builder(context, R.style.CustomAlertDialog).setMessage(R.string.confirm_exit)
                    .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                        leaveRequestListener?.onLeaveRequest(team)
                    }.setNegativeButton(R.string.no, null).show()
            }
        } else {
            joinRequestListener?.onJoinRequest(team)
        }
    }

    fun updateList(result: Pair<List<TeamData>, Map<String, Long>>?) {
        if (result == null) {
            visitCounts = emptyMap()
            submitList(emptyList()) {
                updateCompleteListener?.onUpdateComplete(0)
            }
        } else {
            val (newList, allVisitCounts) = result
            visitCounts = allVisitCounts
            submitList(newList) {
                updateCompleteListener?.onUpdateComplete(newList.size)
            }
        }
    }

    private fun getBundle(team: TeamData): Bundle {
        return Bundle().apply {
            putString("state", if (team.type?.isEmpty() == true) "teams" else "${team.type}s")
            putString("item", team._id)
            putString("parentCode", "dev")
        }
    }

    fun setType(type: String?) {
        this.type = type
    }

    fun cleanup() {
        scope.cancel()
        teamStatusCache.clear()
        visitCountsCache.clear()
    }

    class ViewHolderTeam(val binding: ItemTeamListBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        val TeamDiffCallback = DiffUtils.itemCallback<TeamData>(
            areItemsTheSame = { oldItem, newItem -> oldItem._id == newItem._id },
            areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
        )
    }
}
