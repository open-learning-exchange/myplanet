package org.ole.planet.myplanet.utilities

import android.content.Context
import android.content.Context.LAYOUT_INFLATER_SERVICE
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseExpandableListAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import org.ole.planet.myplanet.R

class ExpandableListAdapter(private val context: Context, private val expandableListTitle: List<String>, private val expandableListDetail: Map<String, List<String>>) : BaseExpandableListAdapter() {
    override fun getGroupCount(): Int {
        return expandableListTitle.size
    }

    override fun getChildrenCount(listPosition: Int): Int {
        return expandableListDetail[expandableListTitle[listPosition]]!!.size
    }

    override fun getGroup(listPosition: Int): Any {
        return expandableListTitle[listPosition]
    }

    override fun getChild(listPosition: Int, expandedListPosition: Int): String {
        return expandableListDetail[expandableListTitle[listPosition]]?.get(expandedListPosition) ?: ""
    }

    override fun getGroupId(listPosition: Int): Long {
        return listPosition.toLong()
    }

    override fun getChildId(listPosition: Int, expandedListPosition: Int): Long {
        return expandedListPosition.toLong()
    }

    override fun hasStableIds(): Boolean {
        return false
    }

    override fun getGroupView(listPosition: Int, isExpanded: Boolean, convertView: View?, parent: ViewGroup): View {
        var groupView = convertView
        val listTitle = getGroup(listPosition) as String
        if (groupView == null) {
            val layoutInflater = context.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            groupView = layoutInflater.inflate(R.layout.group_item, null)
        }
        val listTitleTextView = groupView!!.findViewById<TextView>(R.id.groupTextView)
        listTitleTextView.text = listTitle
        return groupView
    }
    override fun getChildView(
        listPosition: Int,
        expandedListPosition: Int,
        isLastChild: Boolean,
        convertView: View?,
        parent: ViewGroup
    ): View? {
        var childView = convertView
        val expandedListText = getChild(listPosition, expandedListPosition) as String
        Log.d("ollonde", "expandedListText: $expandedListText")
        Log.d("ollonde", "expandableListDetail: $expandableListDetail")

        if (childView == null) {
            val layoutInflater = context.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            childView = layoutInflater.inflate(R.layout.child_item, null)
        }

        val communityLayout = childView?.findViewById<LinearLayout>(R.id.communityLayout)
        val teamListLayout = childView?.findViewById<LinearLayout>(R.id.teamListLayout)
        val teamMessageLayout = childView?.findViewById<LinearLayout>(R.id.teamMessageLayout)

        if (expandedListText == "Community") {
            communityLayout?.visibility = View.VISIBLE
            teamListLayout?.visibility = View.GONE
            teamMessageLayout?.visibility = View.GONE
        } else {
            communityLayout?.visibility = View.GONE
            teamListLayout?.visibility = View.VISIBLE
            teamMessageLayout?.visibility = View.GONE

            val teamListView = childView?.findViewById<ListView>(R.id.teamListView)
            val teamList = expandableListDetail["Share with Team/Enterprises"] ?: listOf()
            Log.d("ollonde", "teamList: $teamList")

            if (teamListView != null) {
                val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, teamList)
                teamListView.adapter = adapter

                teamListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                    val selectedTeam = teamList[position]
                    if (selectedTeam == expandedListText) {
                        teamMessageLayout?.visibility = View.VISIBLE
                    }
                }
            }
        }

        return childView
    }


    override fun isChildSelectable(listPosition: Int, expandedListPosition: Int): Boolean {
        return true
    }
}
