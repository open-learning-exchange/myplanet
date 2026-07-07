package org.ole.planet.myplanet.ui.chat

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R

class ChatShareTargetAdapter(
    private val onItemClick: (ChatShareTargetModel) -> Unit
) : ListAdapter<ChatShareTargetModel, RecyclerView.ViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isGroup) VIEW_TYPE_GROUP else VIEW_TYPE_CHILD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_GROUP) {
            val view = layoutInflater.inflate(R.layout.expandable_list_group, parent, false)
            GroupViewHolder(view)
        } else {
            val view = layoutInflater.inflate(R.layout.expandable_list_item, parent, false)
            ChildViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is GroupViewHolder) {
            holder.bind(item)
            holder.itemView.setOnClickListener { onItemClick(item) }
        } else if (holder is ChildViewHolder) {
            holder.bind(item)
            holder.itemView.setOnClickListener { onItemClick(item) }
        }
    }

    class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val listTitleTextView: TextView = view.findViewById(R.id.listTitle)
        private val arrowIcon: ImageView = view.findViewById(R.id.arrowIcon)

        fun bind(item: ChatShareTargetModel) {
            listTitleTextView.setTypeface(null, Typeface.BOLD)
            listTitleTextView.text = item.title
            listTitleTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.daynight_textColor))
            arrowIcon.rotation = if (item.isExpanded) 180f else 0f
        }
    }

    class ChildViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val expandedListTextView: TextView = view.findViewById(R.id.expandedListItem)
        private val sharedIcon: ImageView = view.findViewById(R.id.sharedIcon)

        fun bind(item: ChatShareTargetModel) {
            expandedListTextView.text = item.title
            itemView.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.multi_select_grey))
            expandedListTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.daynight_textColor))
            sharedIcon.visibility = if (item.isShared) View.VISIBLE else View.GONE
        }
    }

    companion object {
        private const val VIEW_TYPE_GROUP = 0
        private const val VIEW_TYPE_CHILD = 1

        private val DiffCallback = object : DiffUtil.ItemCallback<ChatShareTargetModel>() {
            override fun areItemsTheSame(oldItem: ChatShareTargetModel, newItem: ChatShareTargetModel): Boolean {
                return oldItem.title == newItem.title && oldItem.isGroup == newItem.isGroup
            }

            override fun areContentsTheSame(oldItem: ChatShareTargetModel, newItem: ChatShareTargetModel): Boolean {
                return oldItem == newItem
            }
        }
    }
}
