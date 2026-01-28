package org.ole.planet.myplanet.ui.health

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemUserBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.TimeUtils

class HealthUsersAdapter(private val clickListener: ((RealmUserModel) -> Unit)? = null) :
    ListAdapter<RealmUserModel, HealthUsersAdapter.ViewHolder>(
        DiffUtils.itemCallback<RealmUserModel>(
            areItemsTheSame = { old, new -> old.id == new.id },
            areContentsTheSame = { old, new ->
                old.name == new.name &&
                old.userImage == new.userImage &&
                old.joinDate == new.joinDate
            }
        )
    ) {

    class ViewHolder(private val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: RealmUserModel, clickListener: ((RealmUserModel) -> Unit)?) {
            binding.txtName.text = binding.root.context.getString(R.string.two_strings, user.getFullName(), "(${user.name})")
            binding.txtJoined.text = binding.root.context.getString(R.string.joined_colon, TimeUtils.formatDate(user.joinDate))

            if (!TextUtils.isEmpty(user.userImage)) {
                Glide.with(binding.ivUser.context)
                    .load(user.userImage)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .circleCrop()
                    .placeholder(R.drawable.profile)
                    .error(R.drawable.profile)
                    .into(binding.ivUser)
            } else {
                binding.ivUser.setImageResource(R.drawable.profile)
            }

            binding.root.setOnClickListener {
                clickListener?.invoke(user)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), clickListener)
    }
}
