package org.ole.planet.myplanet.ui.events

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.databinding.RowDescriptionBinding
import org.ole.planet.myplanet.utils.DiffUtils

class EventsDescriptionAdapter : ListAdapter<EventsDescriptionAdapter.DescriptionItem, EventsDescriptionAdapter.ViewHolder>(
    DiffUtils.itemCallback(
        areItemsTheSame = { oldItem, newItem -> oldItem.key == newItem.key },
        areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
    )
) {

    data class DescriptionItem(val key: String, val value: String)

    class ViewHolder(val binding: RowDescriptionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RowDescriptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.title.text = "${item.key} : "
        holder.binding.description.text = item.value
    }
}
