package org.ole.planet.myplanet.ui.team.teamMember

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowJoinedUserBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import io.realm.Realm
import io.realm.Sort
import org.ole.planet.myplanet.utilities.Utilities

class AdapterJoinedMember(private val context: Context, private val list: MutableList<RealmUserModel>, private val mRealm: Realm, private val teamId: String) : RecyclerView.Adapter<AdapterJoinedMember.ViewHolderUser>() {
    private lateinit var rowJoinedUserBinding: RowJoinedUserBinding
    private val currentUser: RealmUserModel = UserProfileDbHandler(context).userModel!!
    private val profileDbHandler = UserProfileDbHandler(context)
    private val teamLeaderId: String? = mRealm.where(RealmMyTeam::class.java)
        .equalTo("teamId", teamId)
        .equalTo("isLeader", true)
        .findFirst()?.userId

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderUser { rowJoinedUserBinding = RowJoinedUserBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderUser(rowJoinedUserBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderUser, position: Int) {
        val member = list[position]
        rowJoinedUserBinding.tvTitle.text = if (member.toString() == " ") member.name else member.toString()
        rowJoinedUserBinding.tvDescription.text = context.getString(R.string.member_description, member.getRoleAsString(), RealmTeamLog.getVisitCount(mRealm, member.name, teamId))
        Glide.with(context)
            .load(list[position].userImage)
            .placeholder(R.drawable.profile)
            .error(R.drawable.profile)
            .into(rowJoinedUserBinding.memberImage)
        if (teamLeaderId == member.id) {
            rowJoinedUserBinding.tvIsLeader.visibility = View.VISIBLE
            rowJoinedUserBinding.tvIsLeader.text = context.getString(R.string.team_leader)
        } else {
            rowJoinedUserBinding.tvIsLeader.visibility = View.GONE
        }
        val isLoggedInUserTeamLeader = teamLeaderId != null && teamLeaderId == currentUser.id
        val overflowMenuOptions = arrayOf(context.getString(R.string.remove), context.getString(R.string.make_leader))
        checkUserAndShowOverflowMenu(position, overflowMenuOptions, isLoggedInUserTeamLeader)
        holder.itemView.setOnClickListener {
            val activity = it.context as AppCompatActivity
            val fragment = MemberDetailFragment.newInstance(
                member.firstName.toString() + " " + member.lastName.toString(),
                member.email.toString(),
                member.dob.toString().substringBefore("T"),
                member.language.toString(),
                member.phoneNumber.toString(),
                profileDbHandler.getOfflineVisits(member).toString(),
                profileDbHandler.getLastVisit(member),
                member.firstName + " " + member.lastName,
                member.level.toString(),
                member.userImage
            )
            activity.supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit()
        }
    }

    private fun checkUserAndShowOverflowMenu(
        position: Int,
        overflowMenuOptions: Array<String>,
        isLoggedInUserTeamLeader: Boolean
    ) {
        if (isLoggedInUserTeamLeader  && list.size>1) {
            rowJoinedUserBinding.icMore.visibility = View.VISIBLE
            rowJoinedUserBinding.icMore.setOnClickListener {
                val builder = AlertDialog.Builder(context, R.style.AlertDialogTheme)
                val adapter = object : ArrayAdapter<CharSequence>(
                    context,
                    android.R.layout.simple_list_item_1,
                    overflowMenuOptions
                ) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = super.getView(position, convertView, parent) as TextView
                        val color = ContextCompat.getColor(context, R.color.daynight_textColor)
                        view.setTextColor(color)
                        return view
                    }
                }
                builder.setAdapter(adapter) { _, i ->
                    if (position >= 0 && position < list.size) {
                        when (i) {
                            0 -> {
                                if (currentUser.id != list[position].id) {
                                    reject(list[position], position)
                                    notifyItemChanged(position)
                                    notifyItemRangeChanged(position, list.size)
                                } else {
                                    val nextOfKin= getNextOfKin()
                                    if(nextOfKin!=null){
                                        makeLeader(nextOfKin)
                                        reject(list[position], position)
                                        notifyItemChanged(position)
                                        notifyItemRangeChanged(position, list.size)
                                    }
                                    else {
                                        Toast.makeText(context, R.string.cannot_remove_user, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            1 -> {
                                makeLeader(list[position])
                                notifyItemChanged(position)
                            }
                            else -> {
                                Toast.makeText(context, R.string.cannot_remove_user, Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, R.string.cannot_remove_user, Toast.LENGTH_SHORT).show()
                    }
                }.setNegativeButton(R.string.dismiss, null).show()
            }
        } else {
            rowJoinedUserBinding.icMore.visibility = View.GONE
        }
    }

    private fun getNextOfKin(): RealmUserModel? {
        val members: List<RealmMyTeam> = mRealm.where(RealmMyTeam::class.java)
            .equalTo("teamId", teamId)
            .equalTo("isLeader", false)
            .notEqualTo("status","archived")
            .sort("createdDate", Sort.DESCENDING)
            .findAll()
        val successor =  if (members.isNotEmpty()) members?.first() else null
        if(successor==null){
            return null
        }
        else{
            val user= mRealm.where(RealmUserModel::class.java).equalTo("id", successor.userId).findFirst()
            return user
        }
        return null
    }

    private fun makeLeader(userModel: RealmUserModel) {
        if(userModel==null){
            Utilities.toast(context, context.getString(R.string.cannot_remove_user))
            return
        }
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
            team?.updated=true
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
        list.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, list.size)
    //        Utilities.toast(context, context.getString(R.string.user_removed_from_team))
    }

    override fun getItemCount(): Int = list.size

    class ViewHolderUser(rowJoinedUserBinding: RowJoinedUserBinding) :
        RecyclerView.ViewHolder(rowJoinedUserBinding.root)
}
