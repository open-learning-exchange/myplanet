package org.ole.planet.myplanet.ui.user

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemUserBinding
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.TimeUtils

class UserArrayAdapter(
    private val onItemClick: (RealmUser) -> Unit
) : ListAdapter<RealmUser, UserArrayAdapter.ViewHolder>(
    DiffUtils.itemCallback(
        areItemsTheSame = { old, new -> old.id == new.id },
        areContentsTheSame = { old, new -> old == new }
    )
) {
    inner class ViewHolder(private val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(um: RealmUser) {
            binding.txtName.text = binding.root.context.getString(R.string.two_strings, um.getFullName(), "(${um.name})")
            binding.txtJoined.text = binding.root.context.getString(R.string.joined_colon, TimeUtils.formatDate(um.joinDate))

            if (!TextUtils.isEmpty(um.userImage)) {
                Glide.with(binding.ivUser.context)
                    .load(um.userImage)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .circleCrop()
                    .placeholder(R.drawable.profile)
                    .error(R.drawable.profile)
                    .into(binding.ivUser)
            } else {
                binding.ivUser.setImageResource(R.drawable.profile)
            }

            binding.root.setOnClickListener {
                onItemClick(um)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
