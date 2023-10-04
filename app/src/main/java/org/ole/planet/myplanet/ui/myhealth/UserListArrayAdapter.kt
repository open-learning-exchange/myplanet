package org.ole.planet.myplanet.ui.myhealth

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.bumptech.glide.Glide
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemUserBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.TimeUtils

class UserListArrayAdapter(private val activity: Activity, private val list: List<RealmUserModel>) :
    ArrayAdapter<RealmUserModel>(activity, R.layout.item_user, list) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val itemUserBinding: ItemUserBinding
        val v: View
        if (convertView == null) {
            itemUserBinding = ItemUserBinding.inflate(LayoutInflater.from(context), parent, false)
            v = itemUserBinding.root
            v.tag = itemUserBinding
        } else {
            itemUserBinding = convertView.tag as ItemUserBinding
            v = convertView
        }

        val um: RealmUserModel? = getItem(position)
        itemUserBinding.txtName.text = """${um?.fullName} (${um?.name})"""
        itemUserBinding.txtJoined.text = "${context.getString(R.string.joined_colon)} ${TimeUtils.formatDate(um!!.joinDate)}"

        if (!um.userImage.isNullOrEmpty()) {
            Glide.with(context)
                .load(um.userImage)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(itemUserBinding.ivUser)
        } else {
            itemUserBinding.ivUser.setImageResource(R.drawable.profile)
        }

        return v
    }
}