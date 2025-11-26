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
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.TeamDiffCallback
import org.ole.planet.myplanet.utilities.TimeUtils

class AdapterTeamList(
    private val context: Context,
    private val fragmentManager: FragmentManager,
    private val teamRepository: TeamRepository,
    private val currentUser: RealmUserModel?,
    private val scope: CoroutineScope,
    private val sharedPrefManager: SharedPrefManager,
    private val teams: List<RealmMyTeam>,
    private val refreshList: () -> Unit
) : ListAdapter<RealmMyTeam, AdapterTeamList.ViewHolderTeam>(TeamDiffCallback()) {
    private val teamCache: MutableMap<String, RealmMyTeam>

    init {
        teamCache = teams.associateBy { it._id!! }.toMutableMap()
    }

    private var type: String? = ""
    private var teamListener: OnClickTeamItem? = null
    private val teamStatusCache = mutableMapOf<String, TeamStatus>()
    private val visitCountsCache = mutableMapOf<String, Long>()
    private var visitCounts: Map<String, Long> = emptyMap()
    private var updateListJob: Job? = null

    data class TeamStatus(
        val isMember: Boolean,
        val isLeader: Boolean,
        val hasPendingRequest: Boolean
    )

    private data class TeamData(
        val _id: String?,
        val name: String?,
        val teamType: String?,
        val createdDate: Long?,
        val type: String?,
        val status: String?,
        val visitCount: Long,
        val teamStatus: TeamStatus?
    )

    interface OnClickTeamItem {
        fun onEditTeam(team: RealmMyTeam?)
    }

    fun setTeamListener(teamListener: OnClickTeamItem?) {
        this.teamListener = teamListener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderTeam {
        val binding = ItemTeamListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderTeam(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderTeam, position: Int) {
        val teamId = getItem(position)._id
        val team = teamCache[teamId] ?: return
        val user: RealmUserModel? = currentUser

        with(holder.binding) {
            created.text = TimeUtils.getFormattedDate(team.createdDate)
            type.text = team.teamType
            type.visibility = if (team.teamType == null) View.GONE else View.VISIBLE
            name.text = team.name
            val visitCount = visitCounts[team._id.orEmpty()] ?: 0L
            noOfVisits.text = context.getString(R.string.number_placeholder, visitCount)

            val teamId = team._id.orEmpty()
            val userId = user?.id
            val cacheKey = "${teamId}_${userId}"
            val teamStatus = teamStatusCache[cacheKey] ?: TeamStatus(
                isMember = false,
                isLeader = false,
                hasPendingRequest = false
            )

            showActionButton(teamStatus.isMember, teamStatus.isLeader, teamStatus.hasPendingRequest, team, user)

            root.setOnClickListener {
                val activity = context as? AppCompatActivity ?: return@setOnClickListener
                val fragment = TeamDetailFragment.newInstance(
                    teamId = "${team._id}",
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
                handleJoinLeaveClick(team, user)
            }
        }
    }

    private fun ItemTeamListBinding.showActionButton(
        isMyTeam: Boolean,
        isTeamLeader: Boolean,
        hasPendingRequest: Boolean,
        team: RealmMyTeam,
        user: RealmUserModel?,
    ) {
        if (isMyTeam) {
            name.setTypeface(null, Typeface.BOLD)
        } else {
            name.setTypeface(null, Typeface.NORMAL)
        }
        when {
            user?.isGuest() == true -> joinLeave.visibility = View.GONE

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

    private fun handleJoinLeaveClick(team: RealmMyTeam, user: RealmUserModel?) {
        val teamId = team._id.orEmpty()
        val userId = user?.id
        val cacheKey = "${teamId}_${userId}"
        val teamStatus = teamStatusCache[cacheKey] ?: TeamStatus(
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
                        leaveTeam(team, userId)
                    }.setNegativeButton(R.string.no, null).show()
            }
        } else {
            requestToJoin(team, user)
        }
        syncTeamActivities()
    }

    private fun requestToJoin(team: RealmMyTeam, user: RealmUserModel?) {
        val teamId = team._id ?: return
        val teamType = team.teamType
        val userId = user?.id
        val userPlanetCode = user?.planetCode
        val cacheKey = "${teamId}_${userId}"
        val position = currentList.indexOfFirst { it._id == teamId }

        teamStatusCache[cacheKey] = TeamStatus(
            isMember = false,
            isLeader = false,
            hasPendingRequest = true
        )

        if (position >= 0) {
            notifyItemChanged(position)
        }

        scope.launch(Dispatchers.IO) {
            teamRepository.requestToJoin(teamId, userId, userPlanetCode, teamType)
            withContext(Dispatchers.Main) {
                refreshList()
            }
        }
    }

    private fun leaveTeam(team: RealmMyTeam, userId: String?) {
        val teamId = team._id ?: return
        val cacheKey = "${teamId}_${userId}"
        teamStatusCache.remove(cacheKey)

        scope.launch(Dispatchers.IO) {
            teamRepository.leaveTeam(teamId, userId)
            withContext(Dispatchers.Main) {
                refreshList()
            }
        }
    }

    private fun syncTeamActivities() {
        MainApplication.applicationScope.launch {
            teamRepository.syncTeamActivities()
        }
    }

    private fun getBundle(team: RealmMyTeam): Bundle {
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
}
