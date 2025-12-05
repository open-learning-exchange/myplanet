package org.ole.planet.myplanet.ui.community

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowJoinedUserBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.ui.team.teamMember.MemberDetailFragment
import org.ole.planet.myplanet.utilities.DiffUtils

internal class AdapterLeader(
    var context: Context,
    private val userProfileDbHandler: UserProfileDbHandler
) : ListAdapter<RealmUserModel, AdapterLeader.ViewHolderLeader>(
    DiffUtils.itemCallback(
        areItemsTheSame = { oldItem, newItem -> oldItem.name == newItem.name },
            areContentsTheSame = { oldItem, newItem ->
                oldItem.firstName == newItem.firstName &&
                    oldItem.lastName == newItem.lastName &&
                    oldItem.email == newItem.email
            }
        )
    ) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderLeader {
        val rowJoinedUserBinding =
            RowJoinedUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderLeader(rowJoinedUserBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderLeader, position: Int) {
        val leader = getItem(position)
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
            NavigationHelper.replaceFragment(
                activity.supportFragmentManager,
                R.id.fragment_container,
                fragment,
                addToBackStack = true
            )
        }
    }

    internal inner class ViewHolderLeader(rowJoinedUserBinding: RowJoinedUserBinding) : RecyclerView.ViewHolder(rowJoinedUserBinding.root) {
        var title = rowJoinedUserBinding.tvTitle
        var tvDescription = rowJoinedUserBinding.tvDescription
        var icon = rowJoinedUserBinding.icMore
    }
}
