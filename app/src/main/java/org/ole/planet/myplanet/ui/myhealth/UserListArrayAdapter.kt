package org.ole.planet.myplanet.ui.myhealth

import android.app.Activity
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.TimeUtils

class UserListArrayAdapter(activity: Activity, val view: Int, var list: List<RealmUserModel>) :
    ArrayAdapter<RealmUserModel>(activity, view, list) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var v: View? = LayoutInflater.from(context).inflate(R.layout.item_user, parent, false)
        val tvName = v?.findViewById<TextView>(R.id.txt_name)
        val joined = v?.findViewById<TextView>(R.id.txt_joined)
        val image = v?.findViewById<ImageView>(R.id.iv_user)
        val um = getItem(position)
        tvName?.text = "${um?.getFullName()} (${um?.name})"
        joined?.text = "${context.getString(R.string.joined_colon)} ${TimeUtils.formatDate(um!!.joinDate)}"

        if (!TextUtils.isEmpty(um.userImage)) {
            Glide.with(context)
                .load(um.userImage)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(image!!)
        } else {
            image?.setImageResource(R.drawable.profile)
        }

        return v!!;
    }
}