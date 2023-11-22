package org.ole.planet.myplanet.ui.userprofile

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.ole.planet.myplanet.MainApplication.context
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.UserListItemBinding
import org.ole.planet.myplanet.model.RealmUserModel

class TeamListAdapter(private val accountsList: List<RealmUserModel>, val context: Context, private val onItemClickListener: OnItemClickListener) : RecyclerView.Adapter<TeamListAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = UserListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    interface OnItemClickListener {
        fun onItemClick(user: RealmUserModel)
    }

    override fun getItemCount(): Int {
        return accountsList.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindView(accountsList[position])

        holder.itemView.setOnClickListener {
            val member = accountsList[position]

            val memberId: String? = member.id

            onItemClickListener.onItemClick(member)
        }
    }

    class ViewHolder(private val binding: UserListItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindView(account: RealmUserModel) {
            if (account.fullName != " ") {
                binding.userNameTextView.text = account.fullName
            } else {
                binding.userNameTextView.text = account.name
            }
            Glide.with(context)
                .load(account.userImage)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(binding.userProfile)
        }
    }
}
