package org.ole.planet.myplanet.ui.chat

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.utils.DiffUtils

sealed class ShareTargetItem {
    data class Group(val title: String, var isExpanded: Boolean = false) : ShareTargetItem()
    data class Child(val title: String, val groupTitle: String, val isShared: Boolean) : ShareTargetItem()
}

class ChatShareTargetAdapter(
    private val context: Context,
    private val sharedChildren: Set<String>,
    private val onChildClicked: (groupTitle: String, childTitle: String) -> Unit
) : ListAdapter<ShareTargetItem, RecyclerView.ViewHolder>(
    DiffUtils.itemCallback<ShareTargetItem>(
        { oldItem, newItem ->
            when {
                oldItem is ShareTargetItem.Group && newItem is ShareTargetItem.Group -> oldItem.title == newItem.title
                oldItem is ShareTargetItem.Child && newItem is ShareTargetItem.Child -> oldItem.title == newItem.title && oldItem.groupTitle == newItem.groupTitle
                else -> false
            }
        },
        { oldItem, newItem -> oldItem == newItem }
    )
) {

    private val groups = mutableMapOf<String, List<ShareTargetItem.Child>>()
    private val expandedGroups = mutableSetOf<String>()

    fun submitData(titleList: List<String>, detailList: HashMap<String, List<String>>) {
        groups.clear()
        titleList.forEach { title ->
            val children = detailList[title] ?: emptyList()
            groups[title] = children.map { child ->
                ShareTargetItem.Child(child, title, sharedChildren.contains(child))
            }
        }
        rebuildList()
    }

    private fun rebuildList() {
        val flatList = mutableListOf<ShareTargetItem>()
        for ((groupTitle, children) in groups) {
            val isExpanded = expandedGroups.contains(groupTitle)
            flatList.add(ShareTargetItem.Group(groupTitle, isExpanded))
            if (isExpanded) {
                flatList.addAll(children)
            }
        }
        submitList(flatList)
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ShareTargetItem.Group -> 0
            is ShareTargetItem.Child -> 1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            GroupViewHolder(inflater.inflate(R.layout.expandable_list_group, parent, false))
        } else {
            ChildViewHolder(inflater.inflate(R.layout.expandable_list_item, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is GroupViewHolder && item is ShareTargetItem.Group) {
            holder.bind(item)
        } else if (holder is ChildViewHolder && item is ShareTargetItem.Child) {
            holder.bind(item)
        }
    }

    inner class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val listTitleTextView: TextView = itemView.findViewById(R.id.listTitle)
        private val arrowIcon: ImageView? = itemView.findViewById(R.id.arrowIcon)

        fun bind(group: ShareTargetItem.Group) {
            listTitleTextView.setTypeface(null, Typeface.BOLD)
            listTitleTextView.text = group.title
            listTitleTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.daynight_textColor))
            arrowIcon?.rotation = if (group.isExpanded) 180f else 0f

            itemView.setOnClickListener {
                if (expandedGroups.contains(group.title)) {
                    expandedGroups.remove(group.title)
                } else {
                    expandedGroups.add(group.title)
                }
                rebuildList()
            }
        }
    }

    inner class ChildViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val expandedListTextView: TextView = itemView.findViewById(R.id.expandedListItem)
        private val sharedIcon: ImageView? = itemView.findViewById(R.id.sharedIcon)

        fun bind(child: ShareTargetItem.Child) {
            expandedListTextView.text = child.title
            itemView.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.multi_select_grey))
            expandedListTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.daynight_textColor))
            sharedIcon?.visibility = if (child.isShared) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                onChildClicked(child.groupTitle, child.title)
            }
        }
    }
}
