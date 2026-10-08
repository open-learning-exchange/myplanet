package org.ole.planet.myplanet.ui.user

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.databinding.RowStatBinding
import org.ole.planet.myplanet.utils.DiffUtils

class StatsAdapter(private val context: Context) : ListAdapter<Pair<String, String?>, StatsAdapter.ViewHolderRowStat>(
    DiffUtils.itemCallback<Pair<String, String?>>(
        { oldItem, newItem -> oldItem.first == newItem.first },
        { oldItem, newItem -> oldItem == newItem }
    )
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderRowStat {
        val rowStatBinding = RowStatBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderRowStat(rowStatBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderRowStat, position: Int) {
        val item = getItem(position)
        holder.rowStatBinding.tvTitle.text = item.first
        holder.rowStatBinding.tvTitle.visibility = View.VISIBLE
        holder.rowStatBinding.tvDescription.text = item.second
        holder.rowStatBinding.divider.visibility = if (position == itemCount - 1) View.GONE else View.VISIBLE
    }

    inner class ViewHolderRowStat(val rowStatBinding: RowStatBinding) : RecyclerView.ViewHolder(rowStatBinding.root)
}
