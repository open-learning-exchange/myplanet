package org.ole.planet.myplanet.ui.team

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemTeamGridBinding
import org.ole.planet.myplanet.databinding.LayoutUserListBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.Utilities
import io.realm.Realm

class AdapterTeam(private val context: Context, private val list: List<RealmMyTeam>, private val mRealm: Realm) : RecyclerView.Adapter<AdapterTeam.ViewHolderTeam>() {
    private var teamSelectedListener: OnTeamSelectedListener? = null
    private var listener: OnUserSelectedListener? = context as? OnUserSelectedListener
    private var users: List<RealmUserModel> = emptyList()

    interface OnTeamSelectedListener {
        fun onSelectedTeam(team: RealmMyTeam)
    }

    fun setTeamSelectedListener(teamSelectedListener: OnTeamSelectedListener?) {
        this.teamSelectedListener = teamSelectedListener
        Utilities.log("Team selected listener ${teamSelectedListener == null}")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderTeam {
        val itemTeamGridBinding = ItemTeamGridBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderTeam(itemTeamGridBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderTeam, position: Int) {
        val team = list[position]
        holder.itemTeamGridBinding.title.text = team.name
        holder.itemView.setOnClickListener {
            teamSelectedListener?.onSelectedTeam(team) ?: showUserList(team)
        }
    }

    private fun showUserList(realmMyTeam: RealmMyTeam) {
        val layoutUserListBinding = LayoutUserListBinding.inflate(LayoutInflater.from(context))
        users = RealmMyTeam.getUsers(realmMyTeam._id!!, mRealm, "")
        setListAdapter(layoutUserListBinding.listUser, users)
        layoutUserListBinding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                users = RealmMyTeam.filterUsers(realmMyTeam._id!!, s.toString(), mRealm)
                setListAdapter(layoutUserListBinding.listUser, users)
            }
            override fun afterTextChanged(s: android.text.Editable) {}
        })
        layoutUserListBinding.listUser.setOnItemClickListener { _, _, position, _ ->
            listener?.onSelectedUser(users[position])
        }
        AlertDialog.Builder(context).setTitle(R.string.select_user_to_login)
            .setView(layoutUserListBinding.root)
            .setNegativeButton(R.string.dismiss, null)
            .show()
    }

    private fun setListAdapter(lv: ListView, users: List<RealmUserModel>) {
        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, users)
        lv.adapter = adapter
    }

    override fun getItemCount(): Int = list.size

    interface OnUserSelectedListener {
        fun onSelectedUser(userModel: RealmUserModel)
    }

    class ViewHolderTeam(val itemTeamGridBinding: ItemTeamGridBinding) :
        RecyclerView.ViewHolder(itemTeamGridBinding.root)
}
