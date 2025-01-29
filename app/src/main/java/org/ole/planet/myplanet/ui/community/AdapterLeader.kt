package org.ole.planet.myplanet.ui.community

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowJoinedUserBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.team.teamMember.MemberDetailFragment

class AdapterLeader(var context: Context, private var leaders: List<RealmUserModel>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private lateinit var rowJoinedUserBinding: RowJoinedUserBinding
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        rowJoinedUserBinding = RowJoinedUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderLeader(rowJoinedUserBinding)
    }

    override fun getItemCount(): Int {
        return leaders.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderLeader) {
            val leader = leaders[position]
            if (leader.firstName == null) {
                holder.title.text = leader.name
            } else {
                holder.title.text = context.getString(R.string.message_placeholder, leader)
            }
            holder.tvDescription.text = leader.email

            holder.itemView.setOnClickListener {
                showLeaderDetails(leader)
            }
        }
    }

    private fun showLeaderDetails(leader: RealmUserModel) {
        val activity = context as? FragmentActivity
        if (activity?.findViewById<View>(R.id.fragment_container) != null) {
            val fragment = MemberDetailFragment.newInstance(
                name = leader.name ?: "",
                email = leader.email ?: "",
                dob = leader.dob ?: "",
                language = leader.language ?: "",
                phone = leader.phoneNumber ?: "",
                visits = "",
                lastLogin = "",
                username = leader.name ?: "",
                memberLevel = leader.level ?: "",
                imageUrl = null
            )
            activity.supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit()
        } else {
            Log.d("AdapterLeader", "Error: Fragment container not found")
            Log.d("AdapterLeader", "Leader: $leader")
        }
    }

    internal inner class ViewHolderLeader(rowJoinedUserBinding: RowJoinedUserBinding) : RecyclerView.ViewHolder(rowJoinedUserBinding.root) {
        var title = rowJoinedUserBinding.tvTitle
        var tvDescription = rowJoinedUserBinding.tvDescription
        var icon = rowJoinedUserBinding.icMore
    }
}
