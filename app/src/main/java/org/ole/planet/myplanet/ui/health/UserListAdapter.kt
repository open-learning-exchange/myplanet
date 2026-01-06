package org.ole.planet.myplanet.ui.health

import android.app.Activity
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.TimeUtils

class UserListAdapter(activity: Activity, val view: Int, var list: List<RealmUserModel>) : ArrayAdapter<RealmUserModel>(activity, view, list) {
    private class ViewHolder {
        var tvName: TextView? = null
        var joined: TextView? = null
        var image: ImageView? = null
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder: ViewHolder
        var convertViewVar = convertView

        if (convertViewVar == null) {
            convertViewVar = LayoutInflater.from(context).inflate(R.layout.item_user, parent, false)
            holder = ViewHolder()
            holder.tvName = convertViewVar.findViewById(R.id.txt_name)
            holder.joined = convertViewVar.findViewById(R.id.txt_joined)
            holder.image = convertViewVar.findViewById(R.id.iv_user)
            convertViewVar.tag = holder
        } else {
            holder = convertViewVar.tag as ViewHolder
        }

        val um = getItem(position)
        holder.tvName?.text = context.getString(R.string.two_strings, um?.getFullName(), "(${um?.name})")
        if (um != null) {
            holder.joined?.text = context.getString(R.string.joined_colon, TimeUtils.formatDate(um.joinDate))
        }

        if (!TextUtils.isEmpty(um?.userImage)) {
            holder.image?.let {
                Glide.with(it.context)
                    .load(um?.userImage)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .circleCrop()
                    .placeholder(R.drawable.profile)
                    .error(R.drawable.profile)
                    .into(it)
            }
        } else {
            holder.image?.setImageResource(R.drawable.profile)
        }

        return convertViewVar!!
    }
}
