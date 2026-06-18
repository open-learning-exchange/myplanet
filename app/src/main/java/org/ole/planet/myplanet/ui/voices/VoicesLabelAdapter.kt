package org.ole.planet.myplanet.ui.voices

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.databinding.ItemVoiceLabelBinding
import org.ole.planet.myplanet.utils.DiffUtils

class VoicesLabelAdapter(
    private val onItemClick: (String) -> Unit
) : ListAdapter<String, VoicesLabelAdapter.ViewHolder>(
    DiffUtils.itemCallback<String>(
        { oldItem, newItem -> oldItem == newItem },
        { oldItem, newItem -> oldItem == newItem }
    )
) {

    class ViewHolder(val binding: ItemVoiceLabelBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVoiceLabelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val label = getItem(position)
        holder.binding.tvLabel.text = label

        holder.itemView.setOnClickListener {
            onItemClick(label)
        }
    }
}
