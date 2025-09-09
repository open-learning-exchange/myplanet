package org.ole.planet.myplanet.ui.userprofile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.UserListItemBinding
import org.ole.planet.myplanet.model.User

class TeamListAdapter(
    private var membersList: MutableList<User>,
    private val onItemClickListener: OnItemClickListener
) : RecyclerView.Adapter<TeamListAdapter.ViewHolder>() {
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
            val currentPosition = holder.bindingAdapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                val member = membersList[currentPosition]
                onItemClickListener.onItemClick(member)
            }
        }
    }

    override fun getItemCount(): Int {
        return membersList.size
    }

    fun updateList(newUserList: MutableList<User>) {
        val diffCallback = TeamListDiffCallback(membersList, newUserList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        membersList.clear()
        membersList.addAll(newUserList)
        diffResult.dispatchUpdatesTo(this)
    }
    
    fun getList(): List<User> = membersList

    class ViewHolder(private val binding: UserListItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindView(account: User) {
            if (account.fullName?.isEmpty() == true || account.fullName == " ") {
                binding.userNameTextView.text = account.name
            } else {
                binding.userNameTextView.text = account.fullName
            }
            Glide.with(binding.userProfile.context)
                .load(account.image)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(binding.userProfile)
        }
    }
    
    private class TeamListDiffCallback(
        private val oldList: List<User>,
        private val newList: List<User>
    ) : DiffUtil.Callback() {
        
        override fun getOldListSize(): Int = oldList.size
        
        override fun getNewListSize(): Int = newList.size
        
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return try {
                oldList[oldItemPosition].name == newList[newItemPosition].name
            } catch (e: Exception) {
                false
            }
        }
        
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return try {
                val oldItem = oldList[oldItemPosition]
                val newItem = newList[newItemPosition]
                oldItem.name == newItem.name &&
                    oldItem.fullName == newItem.fullName &&
                    oldItem.image == newItem.image
            } catch (e: Exception) {
                false
            }
        }
    }
}
