package org.ole.planet.myplanet.ui.news

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.TextView
import org.ole.planet.myplanet.R

class ExpandableListAdapter(
    private val context: Context,
    private val expandableTitleList: List<String>,
    private val expandableDetailList: HashMap<String, List<String>>
) : BaseExpandableListAdapter() {

    override fun getChild(lstPosn: Int, expanded_ListPosition: Int): Any {
        return expandableDetailList[expandableTitleList[lstPosn]]!![expanded_ListPosition]
    }

    override fun getChildId(listPosition: Int, expanded_ListPosition: Int): Long {
        return expanded_ListPosition.toLong()
    }

    override fun getChildView(
        lstPosn: Int,
        expanded_ListPosition: Int,
        isLastChild: Boolean,
        convertView: View?, // Make convertView nullable
        parent: ViewGroup
    ): View {
        var convertView = convertView
        val expandedListText = getChild(lstPosn, expanded_ListPosition) as String
        if (convertView == null) {
            val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            convertView = layoutInflater.inflate(R.layout.expandable_list_item, null)
        }
        val expandedListTextView = convertView!!.findViewById<View>(R.id.expandedListItem) as TextView
        expandedListTextView.text = expandedListText
        return convertView
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

    override fun getGroupView(
        listPosition: Int,
        isExpanded: Boolean,
        convertView: View?, // Make convertView nullable
        parent: ViewGroup
    ): View {
        var convertView = convertView
        val listTitle = getGroup(listPosition) as String
        if (convertView == null) {
            val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            convertView = layoutInflater.inflate(R.layout.expandable_list_group, null)
        }
        val listTitleTextView = convertView!!.findViewById<View>(R.id.listTitle) as TextView
        listTitleTextView.setTypeface(null, Typeface.BOLD)
        listTitleTextView.text = listTitle
        return convertView
    }

    override fun hasStableIds(): Boolean {
        return false
    }

    override fun isChildSelectable(listPosition: Int, expandedListPosition: Int): Boolean {
        return true
    }
}
