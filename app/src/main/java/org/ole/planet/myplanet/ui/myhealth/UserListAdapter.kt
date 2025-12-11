package org.ole.planet.myplanet.ui.myhealth

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.TimeUtils

class UserListAdapter(private val onUserSelected: (RealmUserModel) -> Unit) :
    ListAdapter<RealmUserModel, UserListAdapter.UserViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = getItem(position)
        holder.bind(user)
        holder.itemView.setOnClickListener { onUserSelected(user) }
    }

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.txt_name)
        private val joined: TextView = itemView.findViewById(R.id.txt_joined)
        private val image: ImageView = itemView.findViewById(R.id.iv_user)

        fun bind(um: RealmUserModel) {
            tvName.text = itemView.context.getString(R.string.two_strings, um.getFullName(), "(${um.name})")
            joined.text = itemView.context.getString(R.string.joined_colon, TimeUtils.formatDate(um.joinDate))

            if (!TextUtils.isEmpty(um.userImage)) {
                Glide.with(image.context)
                    .load(um.userImage)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .circleCrop()
                    .placeholder(R.drawable.profile)
                    .error(R.drawable.profile)
                    .into(image)
            } else {
                image.setImageResource(R.drawable.profile)
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = DiffUtils.itemCallback<RealmUserModel>(
            areItemsTheSame = { oldItem, newItem -> oldItem._id == newItem._id },
            areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
        )
    }
}
