package org.ole.planet.myplanet.ui.user

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnUserProfileClickListener
import org.ole.planet.myplanet.databinding.UserListItemBinding
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.ImageUtils

class UsersAdapter(
    private val onItemClickListener: OnUserProfileClickListener
) : ListAdapter<User, UsersAdapter.ViewHolder>(DIFF_CALLBACK) {
    private var avatarSize: Int = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        if (avatarSize == 0) {
            avatarSize = parent.context.resources.getDimensionPixelSize(R.dimen._40dp)
        }
        val binding = UserListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, avatarSize)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val member = getItem(position)
        holder.bindView(member)
    }

    inner class ViewHolder(private val binding: UserListItemBinding, private val avatarSize: Int) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemClickListener.onItemClick(getItem(pos))
                }
            }
        }

        fun bindView(account: User) {
            if (account.fullName?.isEmpty() == true || account.fullName == " ") {
                binding.userNameTextView.text = account.name
            } else {
                binding.userNameTextView.text = account.fullName
            }
            ImageUtils.loadProfileImage(account.image, binding.userProfile, avatarSize)
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
