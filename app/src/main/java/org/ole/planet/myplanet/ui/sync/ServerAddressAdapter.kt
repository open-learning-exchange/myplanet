package org.ole.planet.myplanet.ui.sync

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.ServerAddressesModel

class ServerAddressAdapter(private var serverList: List<ServerAddressesModel>, private val onItemClick: (ServerAddressesModel) -> Unit) : RecyclerView.Adapter<ServerAddressAdapter.ServerViewHolder>() {
    private var selectedPosition: Int = -1

    inner class ServerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val serverName: TextView = itemView.findViewById(R.id.btn_server_address)
        fun bind(serverModel: ServerAddressesModel, isSelected: Boolean) {
            serverName.text = serverModel.name
            serverName.isSelected = isSelected
            if (isSelected) {
                serverName.setBackgroundColor(ContextCompat.getColor(serverName.context, R.color.selected_color))
            } else {
                serverName.setBackgroundColor(ContextCompat.getColor(serverName.context, android.R.color.transparent))
            }
        }
    }

    fun updateList(newList: List<ServerAddressesModel>) {
        serverList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_server_address, parent, false)
        return ServerViewHolder(view)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        val serverAddress = serverList[position]
        holder.bind(serverAddress, position == selectedPosition)
        holder.itemView.setOnClickListener {
            onItemClick(serverAddress)
            setSelectedPosition(position)
        }
    }

    override fun getItemCount(): Int = serverList.size

    fun setSelectedPosition(position: Int) {
        selectedPosition = position
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedPosition = -1
        notifyDataSetChanged()
    }
}
