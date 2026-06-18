package org.ole.planet.myplanet.ui.events

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R

class EventsDescriptionAdapter : ListAdapter<EventsDescriptionAdapter.DescriptionItem, EventsDescriptionAdapter.ViewHolder>(
    object : DiffUtil.ItemCallback<DescriptionItem>() {
        override fun areItemsTheSame(oldItem: DescriptionItem, newItem: DescriptionItem): Boolean {
            return oldItem.key == newItem.key
        }

        override fun areContentsTheSame(oldItem: DescriptionItem, newItem: DescriptionItem): Boolean {
            return oldItem == newItem
        }
    }
) {

    data class DescriptionItem(val key: String, val value: String)

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.title)
        val description: TextView = itemView.findViewById(R.id.description)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.row_description, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.title.text = holder.itemView.context.getString(R.string.message_placeholder, "${item.key} : ")
        holder.description.text = holder.itemView.context.getString(R.string.message_placeholder, item.value)
    }
}
