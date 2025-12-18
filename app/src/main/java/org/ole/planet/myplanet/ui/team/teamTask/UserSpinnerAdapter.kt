package org.ole.planet.myplanet.ui.team.teamTask

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmUserModel

class UserSpinnerAdapter(context: Context, users: List<RealmUserModel>) :
    ArrayAdapter<RealmUserModel>(context, 0, users) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createView(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createView(position, convertView, parent, true)
    }

    private fun createView(position: Int, convertView: View?, parent: ViewGroup, isDropDown: Boolean = false): View {
        val user = getItem(position)
        val view = convertView ?: LayoutInflater.from(context).inflate(
            if (isDropDown) R.layout.item_user_dropdown else R.layout.item_user_spinner,
            parent,
            false
        )

        val textView = view.findViewById<TextView>(android.R.id.text1)
        user?.let {
            textView.text = it.getFullName().ifBlank { it.name }
        }

        return view
    }
}
