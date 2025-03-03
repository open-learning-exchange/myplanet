package org.ole.planet.myplanet.ui.news

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.ole.planet.myplanet.R

class ExpandableListAdapter(private val context: Context, private val expandableTitleList: List<String>, private val expandableDetailList: HashMap<String, List<String>>) : BaseExpandableListAdapter() {
    override fun getChild(lstPosn: Int, expandedListPosition: Int): Any {
        return expandableDetailList[expandableTitleList[lstPosn]]!![expandedListPosition]
    }

    override fun getChildId(listPosition: Int, expandedListPosition: Int): Long {
        return expandedListPosition.toLong()
    }

    override fun getChildView(lstPosn: Int, expandedListPosition: Int, isLastChild: Boolean, convertView: View?, parent: ViewGroup): View {
        var reusedView = convertView
        val expandedListText = getChild(lstPosn, expandedListPosition) as String
        if (reusedView == null) {
            val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            reusedView = layoutInflater.inflate(R.layout.expandable_list_item, parent, false)
        }
        val expandedListTextView = reusedView?.findViewById<View>(R.id.expandedListItem) as TextView
        expandedListTextView.text = expandedListText
        reusedView.setBackgroundColor(ContextCompat.getColor(parent?.context!!, R.color.multi_select_grey))
        expandedListTextView.setTextColor(ContextCompat.getColor(parent.context, R.color.daynight_textColor))
        return reusedView
    }

    override fun getChildrenCount(listPosition: Int): Int {
        return expandableDetailList[expandableTitleList[listPosition]]!!.size
    }

    override fun getGroup(listPosition: Int): Any {
        return expandableTitleList[listPosition]
    }

    override fun getGroupCount(): Int {
        return expandableTitleList.size
    }

    override fun getGroupId(listPosition: Int): Long {
        return listPosition.toLong()
    }

    override fun getGroupView(listPosition: Int, isExpanded: Boolean, convertView: View?, parent: ViewGroup): View {
        var reusedView = convertView
        val listTitle = getGroup(listPosition) as String
        if (reusedView == null) {
            val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            reusedView = layoutInflater.inflate(R.layout.expandable_list_group, parent, false)
        }
        val listTitleTextView = reusedView?.findViewById<View>(R.id.listTitle) as TextView
        val arrowIcon = reusedView?.findViewById<ImageView>(R.id.arrowIcon)
        listTitleTextView.setTypeface(null, Typeface.BOLD)
        listTitleTextView.text = listTitle
        listTitleTextView.setTextColor(ContextCompat.getColor(parent.context, R.color.daynight_textColor))
        arrowIcon?.rotation = if (isExpanded) 180f else 0f
        return reusedView
    }

    override fun hasStableIds(): Boolean {
        return false
    }

    override fun isChildSelectable(listPosition: Int, expandedListPosition: Int): Boolean {
        return true
    }
}
