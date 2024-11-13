package org.ole.planet.myplanet.ui.userprofile

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.UserListItemBinding
import org.ole.planet.myplanet.model.User

class TeamListAdapter(private var membersList: MutableList<User>, val context: Context, private val onItemClickListener: OnItemClickListener) : RecyclerView.Adapter<TeamListAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = UserListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    interface OnItemClickListener {
        fun onItemClick(user: User)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindView(membersList[position])

        holder.itemView.setOnClickListener {
            val member = membersList[position]
            onItemClickListener.onItemClick(member)
        }
    }

    override fun getItemCount(): Int {
        return membersList.size
    }

    fun updateList(newUserList: MutableList<User>) {
        membersList = newUserList
        notifyDataSetChanged()
    }

    class ViewHolder(private val binding: UserListItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindView(account: User) {
            if (account.fullName?.isEmpty() == true || account.fullName == " ") {
                binding.userNameTextView.text = account.name
            } else {
                binding.userNameTextView.text = account.fullName
            }
            Glide.with(MainApplication.context)
                .load(account.image)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(binding.userProfile)
        }
    }
}