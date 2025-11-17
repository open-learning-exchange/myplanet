package org.ole.planet.myplanet.ui.sync

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.ServerAddressesModel
import org.ole.planet.myplanet.utilities.DiffUtils

class ServerAddressAdapter(
    private val onItemClick: (ServerAddressesModel) -> Unit,
    private val onClearDataDialog: (ServerAddressesModel, Int) -> Unit,
    private val urlWithoutProtocol: String?,
) : ListAdapter<ServerAddressesModel, ServerAddressAdapter.ViewHolder>(
    DiffUtils.itemCallback(
        areItemsTheSame = { old, new -> old.url == new.url },
        areContentsTheSame = { old, new -> old == new },
    ),
) {
    private var selectedPosition: Int = -1
    private var lastSelectedPosition: Int = -1

    fun setSelectedPosition(position: Int) {
        val previous = selectedPosition
        lastSelectedPosition = previous
        selectedPosition = position
        if (previous in currentList.indices) {
            notifyItemChanged(previous)
        }
        if (position in currentList.indices) {
            notifyItemChanged(position)
        }
    }

    fun revertSelection() {
        val current = selectedPosition
        selectedPosition = lastSelectedPosition
        if (current in currentList.indices) {
            notifyItemChanged(current)
        }
        if (selectedPosition in currentList.indices) {
            notifyItemChanged(selectedPosition)
        }
    }

    fun clearSelection() {
        val current = selectedPosition
        selectedPosition = -1
        if (current in currentList.indices) {
            notifyItemChanged(current)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_server_address, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val serverAddress = getItem(position)
        holder.bind(serverAddress, position == selectedPosition)
        holder.itemView.setOnClickListener {
            if (!urlWithoutProtocol.isNullOrEmpty() &&
                serverAddress.url.replace(Regex("^https?://"), "") != urlWithoutProtocol
            ) {
                onClearDataDialog(serverAddress, position)
            } else {
                onItemClick(serverAddress)
                setSelectedPosition(position)
            }
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val button: MaterialButton = itemView.findViewById(R.id.btn_server_address)

        fun bind(serverAddress: ServerAddressesModel, isSelected: Boolean) {
            button.text = serverAddress.name
            button.contentDescription =
                context.getString(
                    R.string.server_address_content_description,
                    serverAddress.name,
                )
            button.isSelected = isSelected
            if (isSelected) {
                button.setBackgroundColor(
                    ContextCompat.getColor(button.context, R.color.selected_color),
                )
            } else {
                button.setBackgroundColor(
                    ContextCompat.getColor(button.context, android.R.color.transparent),
                )
            }
        }
    }
}
