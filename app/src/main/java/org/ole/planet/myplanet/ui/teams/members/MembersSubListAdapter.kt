package org.ole.planet.myplanet.ui.teams.members

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.ole.planet.myplanet.R

class MembersSubListAdapter(
    context: Context,
    items: List<CharSequence>
) : ArrayAdapter<CharSequence>(context, android.R.layout.simple_list_item_1, items) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent) as TextView
        val color = ContextCompat.getColor(context, R.color.daynight_textColor)
        view.setTextColor(color)
        return view
    }
}
