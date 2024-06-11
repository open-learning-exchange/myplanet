package org.ole.planet.myplanet.ui.team.teamMember

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowJoinedUserBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import io.realm.Realm
import org.ole.planet.myplanet.utilities.Utilities

class AdapterJoinedMember(
    private val context: Context,
    private val list: List<RealmUserModel>,
    private val mRealm: Realm,
    private val teamId: String
) : RecyclerView.Adapter<AdapterJoinedMember.ViewHolderUser>() {

    private val currentUser: RealmUserModel = UserProfileDbHandler(context).userModel!!
    private val teamLeaderId: String? = mRealm.where(RealmMyTeam::class.java)
        .equalTo("teamId", teamId)
        .equalTo("isLeader", true)
        .findFirst()?.userId

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderUser {
        val rowJoinedUserBinding = RowJoinedUserBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderUser(rowJoinedUserBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderUser, position: Int) {
        val member = list[position]
        holder.binding.tvTitle.text = if (member.toString() == " ") member.name else member.toString()
        holder.binding.tvDescription.text = "${member.getRoleAsString()} (${RealmTeamLog.getVisitCount(mRealm, member.name, teamId)} ${context.getString(R.string.visits)})"

        Glide.with(context)
            .load(member.userImage)
            .placeholder(R.drawable.profile)
            .error(R.drawable.profile)
            .into(holder.binding.memberImage)

        val isLoggedInUserTeamLeader = teamLeaderId != null && teamLeaderId == currentUser.id

        if (teamLeaderId == member.id) {
            holder.binding.tvIsLeader.visibility = View.VISIBLE
            holder.binding.tvIsLeader.text = context.getString(R.string.team_leader)
        } else {
            holder.binding.tvIsLeader.visibility = View.GONE
            val overflowMenuOptions = arrayOf(context.getString(R.string.remove), context.getString(R.string.make_leader))
            checkUserAndShowOverflowMenu(holder, position, overflowMenuOptions, isLoggedInUserTeamLeader)
        }
    }

    private fun checkUserAndShowOverflowMenu(holder: ViewHolderUser, position: Int, overflowMenuOptions: Array<String>, isLoggedInUserTeamLeader: Boolean) {
        if (isLoggedInUserTeamLeader) {
            holder.binding.icMore.visibility = View.VISIBLE
            holder.binding.icMore.setOnClickListener {
                AlertDialog.Builder(context).setItems(overflowMenuOptions) { _, i ->
                    if (i == 0) {
                        reject(list[position], position)
                    } else {
                        makeLeader(list[position])
                    }
                }.setNegativeButton(R.string.dismiss, null).show()
            }
        } else {
            holder.binding.icMore.visibility = View.GONE
        }
    }

    private fun makeLeader(userModel: RealmUserModel) {
        mRealm.executeTransaction { realm ->
            val team = realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("userId", userModel.id)
                .findFirst()
            val teamLeader = realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("isLeader", true)
                .findFirst()
            teamLeader?.isLeader = false
            team?.isLeader = true
        }
        notifyDataSetChanged()
        Utilities.toast(context, context.getString(R.string.leader_selected))
    }

    private fun reject(userModel: RealmUserModel, position: Int) {
        mRealm.executeTransaction {
            val team = it.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("userId", userModel.id)
                .findFirst()
            team?.deleteFromRealm()
        }
        list.toMutableList().removeAt(position)
        notifyDataSetChanged()
        Utilities.toast(context, context.getString(R.string.user_removed_from_team))
    }

    override fun getItemCount(): Int = list.size

    class ViewHolderUser(val binding: RowJoinedUserBinding) : RecyclerView.ViewHolder(binding.root)
}
