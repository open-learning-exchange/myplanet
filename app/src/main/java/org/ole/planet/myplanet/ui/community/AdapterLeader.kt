package org.ole.planet.myplanet.ui.community

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowJoinedUserBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.team.teamMember.MemberDetailFragment

class AdapterLeader(var context: Context, private var leaders: List<RealmUserModel>) :
    RecyclerView.Adapter<AdapterLeader.ViewHolderLeader>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderLeader {
        val binding = RowJoinedUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderLeader(binding)
    }

    override fun getItemCount(): Int {
        return leaders.size
    }

    override fun onBindViewHolder(holder: ViewHolderLeader, position: Int) {
        val leader = leaders[position]
        holder.bind(leader)

        holder.itemView.setOnClickListener {
            val fragment = MemberDetailFragment.newInstance(
                name = leader.name ?: "",
                email = leader.email ?: "",
                dob = "", // Add dob if available
                language = "", // Add language if available
                phone = "", // Add phone if available
                visits = "", // Add visits if available
                lastLogin = "", // Add lastLogin if available
                username = leader.name ?: "",
                memberLevel = "", // Add memberLevel if available
                imageUrl = null // Add imageUrl if available
            )
            val transaction = (context as FragmentActivity).supportFragmentManager.beginTransaction()
            transaction.replace(R.id.fragment_container, fragment)
            transaction.addToBackStack(null)
            transaction.commit()
        }
    }

    inner class ViewHolderLeader(private val binding: RowJoinedUserBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(leader: RealmUserModel) {
            binding.tvTitle.text = leader.firstName ?: leader.name
            binding.tvDescription.text = leader.email

            // Load profile image using Glide
            Glide.with(context)
                .load(leader.userImage)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(binding.memberImage)

            // Optionally, set other details if available
        }
    }
}
