package org.ole.planet.myplanet.ui.voices

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemVoiceLabelBinding
import org.ole.planet.myplanet.utils.DiffUtils

class VoicesLabelAdapter(
    private var selectedLabel: String = "All",
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

        val context = holder.itemView.context
        if (label == selectedLabel) {
            holder.binding.tvLabel.background = ContextCompat.getDrawable(context, R.drawable.bg_primary)
            holder.binding.tvLabel.setTextColor(ContextCompat.getColor(context, android.R.color.white))
        } else {
            holder.binding.tvLabel.background = ContextCompat.getDrawable(context, R.drawable.bg_grey)
            holder.binding.tvLabel.setTextColor(ContextCompat.getColor(context, R.color.daynight_textColor))
        }

        holder.itemView.setOnClickListener {
            onItemClick(label)
        }
    }

    fun setSelectedLabel(label: String) {
        val oldSelectedLabel = selectedLabel
        selectedLabel = label

        val oldPosition = currentList.indexOf(oldSelectedLabel)
        val newPosition = currentList.indexOf(selectedLabel)

        if (oldPosition != -1) notifyItemChanged(oldPosition)
        if (newPosition != -1) notifyItemChanged(newPosition)
    }
}
