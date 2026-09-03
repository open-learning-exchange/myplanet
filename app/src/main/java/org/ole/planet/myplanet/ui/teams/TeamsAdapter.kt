package org.ole.planet.myplanet.ui.teams

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemTeamListBinding
import org.ole.planet.myplanet.model.TeamDetails
import org.ole.planet.myplanet.model.TeamStatus
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.StableIdGenerator
import org.ole.planet.myplanet.utils.TimeUtils
import java.io.File

class TeamsAdapter(
    private val isGuestUser: Boolean,
    private val onItemClick: (TeamDetails) -> Unit,
    private val onFeedbackClick: (TeamDetails) -> Unit,
    private val onEditTeamClick: (TeamDetails) -> Unit,
    private val onLeaveTeamClick: (TeamDetails) -> Unit,
    private val onRequestToJoinClick: (TeamDetails) -> Unit
) : ListAdapter<TeamDetails, TeamsAdapter.TeamsViewHolder>(DIFF_CALLBACK) {
    private var type: String? = ""
    private val dateCache = mutableMapOf<Long, String>()
    private var actionEditString: String? = null
    private var actionLeaveString: String? = null
    private var actionRequestedString: String? = null
    private var actionRequestToJoinString: String? = null
    private var pendingColor: Int = 0

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        val item = getItem(position)
        val id = StableIdGenerator.generateStringId(item._id)
        return if (id != RecyclerView.NO_ID) id else StableIdGenerator.generateFallbackId(item)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeamsViewHolder {
        if (actionEditString == null) {
            val context = parent.context
            actionEditString = context.getString(R.string.edit)
            actionLeaveString = context.getString(R.string.leave)
            actionRequestedString = context.getString(R.string.requested)
            actionRequestToJoinString = context.getString(R.string.request_to_join)
            pendingColor = ContextCompat.getColor(context, R.color.pending_request_indicator)
        }
        val binding = ItemTeamListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TeamsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TeamsViewHolder, position: Int) {
        val team = getItem(position)

        with(holder.binding) {
            created.text = dateCache.getOrPut(team.createdDate ?: 0L) { TimeUtils.getFormattedDate(team.createdDate ?: 0) }
            description.text = team.description
            type.text = team.teamType
            type.visibility = if (team.teamType == null) View.GONE else View.VISIBLE
            name.text = team.name
           noOfVisits.text = root.context.getString(R.string.visit_count_placeholder, team.visitCount)

            if (!team.profileImage.isNullOrBlank()) {
                val file = File(team.profileImage)
                if (file.exists()) {
                    Glide.with(root.context)
                        .load(file)
                        .placeholder(R.drawable.ole_logo)
                        .error(R.drawable.ole_logo)
                        .circleCrop()
                        .into(teamPhoto)
                } else {
                    try {
                        val uri = Uri.parse(team.profileImage)
                        Glide.with(root.context)
                            .load(uri)
                            .placeholder(R.drawable.ole_logo)
                            .error(R.drawable.ole_logo)
                            .circleCrop()
                            .into(teamPhoto)
                    } catch (e: Exception) {
                        teamPhoto.setImageResource(R.drawable.ole_logo)
                    }
                }
            } else {
                teamPhoto.setImageResource(R.drawable.ole_logo)
            }

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
                    contentDescription = "$actionEditString ${team.name}"
                    visibility = View.VISIBLE
                    setImageResource(R.drawable.ic_edit)
                    imageTintList = ContextCompat.getColorStateList(context, R.color.daynight_textColor)
                }
            }

            isMyTeam && !isTeamLeader -> {
                joinLeave.apply {
                    isEnabled = true
                    contentDescription = "$actionLeaveString ${team.name}"
                    visibility = View.VISIBLE
                    setImageResource(R.drawable.logout)
                    imageTintList = ContextCompat.getColorStateList(context, R.color.daynight_textColor)
                }
            }

            !isMyTeam && hasPendingRequest -> {
                joinLeave.apply {
                    isEnabled = false
                    contentDescription = "$actionRequestedString ${team.name}"
                    visibility = View.VISIBLE
                    setImageResource(R.drawable.baseline_hourglass_top_24)
                    setColorFilter(pendingColor, PorterDuff.Mode.SRC_IN)
                }
            }

            !isMyTeam -> {
                joinLeave.apply {
                    isEnabled = true
                    contentDescription = "$actionRequestToJoinString ${team.name}"
                    visibility = View.VISIBLE
                    setImageResource(R.drawable.ic_join_request)
                    imageTintList = ContextCompat.getColorStateList(context, R.color.daynight_textColor)
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
        private val DIFF_CALLBACK = DiffUtils.itemCallback<TeamDetails>(
            areItemsTheSame = { oldItem, newItem -> oldItem._id == newItem._id },
            areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
        )
    }
}
