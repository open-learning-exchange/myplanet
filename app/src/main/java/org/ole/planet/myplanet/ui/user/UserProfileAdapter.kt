package org.ole.planet.myplanet.ui.user

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.UserListItemBinding
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.utilities.DiffUtils

class UserProfileAdapter(
    private val onItemClickListener: OnItemClickListener
) : ListAdapter<User, UserProfileAdapter.ViewHolder>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = UserListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    interface OnItemClickListener {
        fun onItemClick(user: User)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val member = getItem(position)
        holder.bindView(member)

        holder.itemView.setOnClickListener {
            val currentPosition = holder.bindingAdapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                val member = getItem(currentPosition)
                onItemClickListener.onItemClick(member)
            }
        }
    }

    fun getList(): List<User> = currentList

    class ViewHolder(private val binding: UserListItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindView(account: User) {
            if (account.fullName?.isEmpty() == true || account.fullName == " ") {
                binding.userNameTextView.text = account.name
            } else {
                binding.userNameTextView.text = account.fullName
            }
            Glide.with(binding.userProfile.context)
                .load(account.image)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .circleCrop()
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(binding.userProfile)
        }
    }

    companion object {
        private val DIFF_CALLBACK = DiffUtils.itemCallback<User>(
            areItemsTheSame = { old, new -> old.name == new.name },
            areContentsTheSame = { old, new ->
                old.name == new.name &&
                        old.fullName == new.fullName &&
                        old.image == new.image
            }
        )
    }
}
