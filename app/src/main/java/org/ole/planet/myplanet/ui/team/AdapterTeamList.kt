package org.ole.planet.myplanet.ui.team

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment
import org.ole.planet.myplanet.utilities.TimeUtils

class AdapterTeamList(private val context: Context, private val list: List<RealmMyTeam>, private val mRealm: Realm, fragmentManager: FragmentManager) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val user: RealmUserModel
    private var type: String? = ""
    private val fragmentManager: FragmentManager
    private var teamListener: OnClickTeamItem? = null

    interface OnClickTeamItem {
        fun onEditTeam(team: RealmMyTeam?)
    }

    fun setTeamListener(teamListener: OnClickTeamItem?) {
        this.teamListener = teamListener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v = LayoutInflater.from(context).inflate(R.layout.item_team_list, parent, false)
        return ViewHolderTeam(v)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderTeam) {
            holder.created.text = TimeUtils.getFormatedDate(list[position].createdDate)
            holder.type.text = list[position].teamType
            holder.type.visibility = if (type == null) View.VISIBLE else View.GONE
            holder.editTeam.visibility = if (RealmMyTeam.getTeamLeader(list[position]._id, mRealm) == user.id) View.VISIBLE else View.GONE
            holder.name.text = list[position].name
            holder.noOfVisits.text = RealmTeamLog.getVisitByTeam(mRealm, list[position]._id).toString() + ""
            val isMyTeam = list[position].isMyTeam(user.id, mRealm)
            showActionButton(isMyTeam, holder, position)
            holder.itemView.setOnClickListener { view: View? ->
                if (context is OnHomeItemClickListener) {
                    val f = TeamDetailFragment()
                    val b = Bundle()
                    b.putString("id", list[position]._id)
                    b.putBoolean("isMyTeam", isMyTeam)
                    f.arguments = b
                    (context as OnHomeItemClickListener).openCallFragment(f)
                }
            }
            holder.feedback.setOnClickListener { v2: View? ->
                val feedbackFragment = FeedbackFragment()
                feedbackFragment.show(fragmentManager, "")
                feedbackFragment.arguments = getBundle(list[position])
            }
            holder.editTeam.setOnClickListener { view: View? -> teamListener!!.onEditTeam(list[position]) }
        }
    }

    fun getBundle(team: RealmMyTeam): Bundle {
        val bundle = Bundle()
        if (team.type.isEmpty()) bundle.putString("state", "teams") else bundle.putString("state", team.type + "s")
        bundle.putString("item", team._id)
        bundle.putString("parentCode", "dev")
        return bundle
    }

    private fun showActionButton(isMyTeam: Boolean, holder: RecyclerView.ViewHolder, position: Int) {
        if (isMyTeam) {
            if (RealmMyTeam.isTeamLeader(list[position].teamId, user.id, mRealm)) {
                (holder as ViewHolderTeam).action.text = "Leave"
                holder.action.setOnClickListener { view: View? ->
                    AlertDialog.Builder(context).setMessage(R.string.confirm_exit).setPositiveButton("Yes") { dialogInterface: DialogInterface?, i: Int ->
                        list[position].leave(user, mRealm)
                        notifyDataSetChanged()
                    }.setNegativeButton("No", null).show()
                }
            } else {
                (holder as ViewHolderTeam).action.visibility = View.GONE
                return
            }
        } else if (list[position].requested(user.id, mRealm)) {
            (holder as ViewHolderTeam).action.text = "Requested"
            holder.action.isEnabled = false
        } else {
            (holder as ViewHolderTeam).action.text = "Request to Join"
            holder.action.setOnClickListener { view: View? ->
                RealmMyTeam.requestToJoin(list[position]._id, user, mRealm)
                notifyDataSetChanged()
            }
        }
        holder.action.visibility = View.VISIBLE
    }

    override fun getItemCount(): Int {
        return list.size
    }

    fun setType(type: String?) {
        this.type = type
    }

    internal inner class ViewHolderTeam(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var name: TextView
        var created: TextView
        var type: TextView
        var noOfVisits: TextView
        var action: Button
        var feedback: Button
        var editTeam: Button

        init {
            name = itemView.findViewById(R.id.name)
            created = itemView.findViewById(R.id.created)
            type = itemView.findViewById(R.id.type)
            action = itemView.findViewById(R.id.join_leave)
            editTeam = itemView.findViewById(R.id.edit_team)
            noOfVisits = itemView.findViewById(R.id.no_of_visits)
            feedback = itemView.findViewById(R.id.btn_feedback)
        }
    }

    init {
        user = UserProfileDbHandler(context).userModel
        this.fragmentManager = fragmentManager
    }
}