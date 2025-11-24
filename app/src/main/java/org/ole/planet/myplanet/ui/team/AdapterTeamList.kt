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
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.TimeUtils

data class TeamViewData(
    val team: RealmMyTeam,
    val status: AdapterTeamList.TeamStatus,
    val visitCount: Long
)

class AdapterTeamList(
    private val context: Context,
    private val fragmentManager: FragmentManager,
    private val currentUser: RealmUserModel?,
    private val scope: CoroutineScope,
) : ListAdapter<TeamViewData, AdapterTeamList.ViewHolderTeam>(DIFF_CALLBACK) {
    private var type: String? = ""
    private var teamListener: OnClickTeamItem? = null
    private var teamActionsListener: OnTeamActionsListener? = null
    private var updateCompleteListener: OnUpdateCompleteListener? = null
    private lateinit var prefData: SharedPrefManager

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

    interface OnTeamActionsListener {
        fun onJoinClick(team: RealmMyTeam, user: RealmUserModel?)
        fun onLeaveClick(team: RealmMyTeam, user: RealmUserModel?)
    }

    interface OnUpdateCompleteListener {
        fun onUpdateComplete(itemCount: Int)
    }

    fun setTeamListener(teamListener: OnClickTeamItem?) {
        this.teamListener = teamListener
    }

    fun setTeamActionsListener(listener: OnTeamActionsListener?) {
        this.teamActionsListener = listener
    }

    fun setUpdateCompleteListener(listener: OnUpdateCompleteListener?) {
        this.updateCompleteListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderTeam {
        val binding = ItemTeamListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        prefData = SharedPrefManager(context)
        return ViewHolderTeam(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderTeam, position: Int) {
        val teamViewData = getItem(position)
        val team = teamViewData.team
        val user: RealmUserModel? = currentUser

        with(holder.binding) {
            created.text = TimeUtils.getFormattedDate(team.createdDate)
            type.text = team.teamType
            type.visibility = if (team.teamType == null) View.GONE else View.VISIBLE
            name.text = team.name
            noOfVisits.text = context.getString(R.string.number_placeholder, teamViewData.visitCount)

            showActionButton(teamViewData.status.isMember, teamViewData.status.isLeader, teamViewData.status.hasPendingRequest, team, user)

            root.setOnClickListener {
                val activity = context as? AppCompatActivity ?: return@setOnClickListener
                val fragment = TeamDetailFragment.newInstance(
                    teamId = "${team._id}",
                    teamName = "${team.name}",
                    teamType = "${team.type}",
                    isMyTeam = teamViewData.status.isMember
                )
                NavigationHelper.replaceFragment(
                    activity.supportFragmentManager,
                    R.id.fragment_container,
                    fragment,
                    addToBackStack = true,
                    tag = "TeamDetailFragment"
                )
                prefData.setTeamName(team.name)
            }

            btnFeedback.setOnClickListener {
                val feedbackFragment = FeedbackFragment()
                feedbackFragment.show(fragmentManager, "")
                feedbackFragment.arguments = getBundle(team)
            }

            joinLeave.setOnClickListener {
                handleJoinLeaveClick(team, user, teamViewData.status)
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

    private fun handleJoinLeaveClick(team: RealmMyTeam, user: RealmUserModel?, teamStatus: TeamStatus) {
        if (teamStatus.isMember) {
            if (teamStatus.isLeader) {
                teamListener?.onEditTeam(team)
            } else {
                AlertDialog.Builder(context, R.style.CustomAlertDialog).setMessage(R.string.confirm_exit)
                    .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                        teamActionsListener?.onLeaveClick(team, user)
                    }.setNegativeButton(R.string.no, null).show()
            }
        } else {
            teamActionsListener?.onJoinClick(team, user)
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

    class ViewHolderTeam(val binding: ItemTeamListBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        val DIFF_CALLBACK = DiffUtils.itemCallback<TeamViewData>(
            areItemsTheSame = { old, new -> old.team._id == new.team._id },
            areContentsTheSame = { old, new -> old == new }
        )
    }
}
