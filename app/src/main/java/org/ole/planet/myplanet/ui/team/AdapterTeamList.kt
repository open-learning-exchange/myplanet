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
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemTeamListBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.TimeUtils

class AdapterTeamList(
    private val context: Context,
    private val list: List<RealmMyTeam>,
    private val mRealm: Realm,
    private val fragmentManager: FragmentManager,
    private val teamRepository: TeamRepository,
) : RecyclerView.Adapter<AdapterTeamList.ViewHolderTeam>() {
    private lateinit var itemTeamListBinding: ItemTeamListBinding
    private var type: String? = ""
    private var teamListener: OnClickTeamItem? = null
    private var filteredList: List<RealmMyTeam> = emptyList()
    private lateinit var prefData: SharedPrefManager

    interface OnClickTeamItem {
        fun onEditTeam(team: RealmMyTeam?)
    }

    fun setTeamListener(teamListener: OnClickTeamItem?) {
        this.teamListener = teamListener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderTeam {
        itemTeamListBinding = ItemTeamListBinding.inflate(LayoutInflater.from(context), parent, false)
        prefData = SharedPrefManager(context)
        return ViewHolderTeam(itemTeamListBinding)
    }

    init {
        updateList()
    }

    override fun onBindViewHolder(holder: ViewHolderTeam, position: Int) {
        val team = filteredList[position]
        val user: RealmUserModel? = UserProfileDbHandler(context).userModel

        with(holder.binding) {
            created.text = TimeUtils.getFormattedDate(team.createdDate)
            type.text = team.teamType
            type.visibility = if (team.teamType == null) View.GONE else View.VISIBLE
            name.text = team.name
            noOfVisits.text = context.getString(R.string.number_placeholder, RealmTeamLog.getVisitByTeam(mRealm, team._id))

            val teamId = team._id
            val userId = user?.id
            val isMyTeam = isMemberOfTeam(teamId, userId)
            val isTeamLeader = isUserTeamLeader(teamId, userId)
            val hasPendingRequest = hasPendingRequest(teamId, userId)
            showActionButton(isMyTeam, isTeamLeader, hasPendingRequest, team, user)

            root.setOnClickListener {
                val activity = context as? AppCompatActivity ?: return@setOnClickListener
                val fragment = TeamDetailFragment.newInstance(
                    teamId = "${team._id}",
                    teamName = "${team.name}",
                    teamType = "${team.type}",
                    isMyTeam = isMyTeam
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

            isTeamLeader -> {
                joinLeave.apply {
                    isEnabled = true
                    contentDescription = "${context.getString(R.string.edit)} ${team.name}"
                    visibility = View.VISIBLE
                    setImageResource(R.drawable.ic_edit)
                    clearColorFilter()
                }
            }

            else -> joinLeave.visibility = View.GONE
        }
    }

    private fun handleJoinLeaveClick(team: RealmMyTeam, user: RealmUserModel?) {
        val teamId = team._id
        val userId = user?.id
        val isMyTeam = isMemberOfTeam(teamId, userId)
        if (isMyTeam) {
            if (isUserTeamLeader(teamId, userId)) {
                teamListener?.onEditTeam(team)
            } else {
                AlertDialog.Builder(context, R.style.CustomAlertDialog).setMessage(R.string.confirm_exit)
                    .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                        leaveTeam(team, userId)
                        updateList()
                    }.setNegativeButton(R.string.no, null).show()
            }
        } else {
            requestToJoin(team, user)
            updateList()
        }
        syncTeamActivities()
    }

    private fun updateList() {
        val user: RealmUserModel? = UserProfileDbHandler(context).userModel
        val userId = user?.id

        val validTeams = list.filter { it.status?.isNotEmpty() == true }
        filteredList = validTeams.sortedWith(compareByDescending<RealmMyTeam> { team ->
            when {
                isUserTeamLeader(team._id, userId) -> 3
                isMemberOfTeam(team._id, userId) -> 2
                else -> 1
            }
        }.thenByDescending { team ->
            RealmTeamLog.getVisitByTeam(mRealm, team._id)
        })
        notifyDataSetChanged()
    }

    private fun isMemberOfTeam(teamId: String?, userId: String?): Boolean {
        if (teamId.isNullOrBlank()) return false
        return runBlocking { teamRepository.isMember(userId, teamId) }
    }

    private fun isUserTeamLeader(teamId: String?, userId: String?): Boolean {
        if (teamId.isNullOrBlank()) return false
        return runBlocking { teamRepository.isTeamLeader(teamId, userId) }
    }

    private fun hasPendingRequest(teamId: String?, userId: String?): Boolean {
        if (teamId.isNullOrBlank()) return false
        return runBlocking { teamRepository.hasPendingRequest(teamId, userId) }
    }

    private fun requestToJoin(team: RealmMyTeam, user: RealmUserModel?) {
        val teamId = team._id ?: return
        runBlocking { teamRepository.requestToJoin(teamId, user, team.teamType) }
    }

    private fun leaveTeam(team: RealmMyTeam, userId: String?) {
        val teamId = team._id ?: return
        runBlocking { teamRepository.leaveTeam(teamId, userId) }
    }

    private fun syncTeamActivities() {
        MainApplication.applicationScope.launch {
            teamRepository.syncTeamActivities(context)
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

    override fun getItemCount(): Int = filteredList.size

    class ViewHolderTeam(val binding: ItemTeamListBinding) : RecyclerView.ViewHolder(binding.root)
}
