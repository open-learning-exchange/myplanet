package org.ole.planet.myplanet.ui.myhealth

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.UserListItemBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.DiffUtils

class UserListAdapter(
    private val onUserSelected: (RealmUserModel) -> Unit
) : ListAdapter<RealmUserModel, UserListAdapter.ViewHolder>(
    DiffUtils.itemCallback(
        areItemsTheSame = { old, new -> old._id == new._id },
        areContentsTheSame = { old, new -> old == new }
    )
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = UserListItemBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = getItem(position)
        holder.bind(user)
        holder.itemView.setOnClickListener { onUserSelected(user) }
    }

    class ViewHolder(private val binding: UserListItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: RealmUserModel) {
            binding.userNameTextView.text = user.getFullName()
            // Picasso.get().load(user.userImage).placeholder(R.drawable.profile).into(binding.userProfile)
        }
    }
}
