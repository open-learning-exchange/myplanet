package org.ole.planet.myplanet.ui.teams

import android.graphics.PorterDuff
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemTeamListBinding
import org.ole.planet.myplanet.model.TeamDetails
import org.ole.planet.myplanet.model.TeamStatus
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.TimeUtils

class TeamsAdapter(
    private val isGuestUser: Boolean,
    private val onItemClick: (TeamDetails) -> Unit,
    private val onFeedbackClick: (TeamDetails) -> Unit,
    private val onEditTeamClick: (TeamDetails) -> Unit,
    private val onLeaveTeamClick: (TeamDetails) -> Unit,
    private val onRequestToJoinClick: (TeamDetails) -> Unit
) : ListAdapter<TeamDetails, TeamsAdapter.TeamsViewHolder>(TeamDiffCallback) {
    private var type: String? = ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeamsViewHolder {
        val binding = ItemTeamListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TeamsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TeamsViewHolder, position: Int) {
        val team = getItem(position)

        with(holder.binding) {
            created.text = TimeUtils.getFormattedDate(team.createdDate ?: 0)
            type.text = team.teamType
            type.visibility = if (team.teamType == null) View.GONE else View.VISIBLE
            name.text = team.name
            noOfVisits.text = root.context.getString(R.string.number_placeholder, team.visitCount)

            val teamStatus = team.teamStatus ?: TeamStatus(
                isMember = false,
                isLeader = false,
                hasPendingRequest = false
            )

            showActionButton(teamStatus.isMember, teamStatus.isLeader, teamStatus.hasPendingRequest, team)

            root.setOnClickListener {
                onItemClick(team)
            }

            btnFeedback.setOnClickListener {
                onFeedbackClick(team)
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
        team: TeamDetails,
    ) {
        val context = root.context
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

    private fun handleJoinLeaveClick(team: TeamDetails) {
        val teamStatus = team.teamStatus ?: return
        when {
            teamStatus.isLeader -> onEditTeamClick(team)
            teamStatus.isMember -> {
                onLeaveTeamClick(team)
            }
            else -> onRequestToJoinClick(team)
        }
    }

    fun setType(type: String?) {
        this.type = type
    }

    class TeamsViewHolder(val binding: ItemTeamListBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        val TeamDiffCallback = DiffUtils.itemCallback<TeamDetails>(
            areItemsTheSame = { oldItem, newItem -> oldItem._id == newItem._id },
            areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
        )
    }
}
