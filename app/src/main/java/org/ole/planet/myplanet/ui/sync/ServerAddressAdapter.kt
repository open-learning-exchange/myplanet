package org.ole.planet.myplanet.ui.sync

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemServerAddressBinding
import org.ole.planet.myplanet.model.ServerAddress
import org.ole.planet.myplanet.utils.DiffUtils

class ServerAddressAdapter(
    private val context: Context,
    private val onItemClick: (ServerAddress) -> Unit,
    private val onClearDataDialog: (ServerAddress, Int) -> Unit,
    private val isServerAlreadyConfigured: Boolean, // ← simple flag instead of URL
) : ListAdapter<ServerAddress, ServerAddressAdapter.ViewHolder>(
    DiffUtils.itemCallback(
        areItemsTheSame = { old, new -> old.url == new.url },
        areContentsTheSame = { old, new -> old == new },
    ),
) {
    private var selectedPosition: Int = -1
    private var lastSelectedPosition: Int = -1

    private val selectedColor by lazy(LazyThreadSafetyMode.NONE) {
        ContextCompat.getColor(context, R.color.selected_color)
    }
    private val transparentColor by lazy(LazyThreadSafetyMode.NONE) {
        ContextCompat.getColor(context, android.R.color.transparent)
    }

    fun setSelectedPosition(position: Int) {
        val previous = selectedPosition
        lastSelectedPosition = previous
        selectedPosition = position
        if (previous in currentList.indices) {
            notifyItemChanged(previous, SELECTION_PAYLOAD)
        }
        if (position in currentList.indices) {
            notifyItemChanged(position, SELECTION_PAYLOAD)
        }
    }

    fun revertSelection() {
        val current = selectedPosition
        selectedPosition = lastSelectedPosition
        if (current in currentList.indices) {
            notifyItemChanged(current, SELECTION_PAYLOAD)
        }
        if (selectedPosition in currentList.indices) {
            notifyItemChanged(selectedPosition, SELECTION_PAYLOAD)
        }
    }

    fun clearSelection() {
        val current = selectedPosition
        selectedPosition = -1
        if (current in currentList.indices) {
            notifyItemChanged(current, SELECTION_PAYLOAD)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemServerAddressBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(SELECTION_PAYLOAD)) {
            holder.updateSelectionState(position == selectedPosition)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val serverAddress = getItem(position)
        holder.bind(serverAddress, position == selectedPosition) // ← only selectedPosition matters
        holder.itemView.setOnClickListener {
            if (isServerAlreadyConfigured && position != selectedPosition) {
                // user is clicking a DIFFERENT server than currently selected → warn them
                onClearDataDialog(serverAddress, position)
            } else {
                // either no server configured yet, or clicking the already selected one
                onItemClick(serverAddress)
                setSelectedPosition(position)
            }
        }
    }

    inner class ViewHolder(val binding: ItemServerAddressBinding) : RecyclerView.ViewHolder(binding.root) {
        private val button get() = binding.btnServerAddress
        fun bind(serverAddress: ServerAddress, isSelected: Boolean) {
            button.text = serverAddress.name
            button.contentDescription =
                itemView.context.getString(
                    R.string.server_address_content_description,
                    serverAddress.name,
                )
            updateSelectionState(isSelected)
        }

        fun updateSelectionState(isSelected: Boolean) {
            button.isSelected = isSelected
            if (isSelected) {
                button.setBackgroundColor(selectedColor)
            } else {
                button.setBackgroundColor(transparentColor)
            }
        }
    }

    companion object {
        private const val SELECTION_PAYLOAD = "selection_payload"
    }
}
