package org.ole.planet.myplanet.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemLibraryHomeBinding
import org.ole.planet.myplanet.model.MyLibraryItem

class LibraryListAdapter(private val onLibraryItemClickListener: OnLibraryItemClickListener) :
    ListAdapter<MyLibraryItem, LibraryListAdapter.ViewHolder>(LibraryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemLibraryHomeBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, position)
    }
    inner class ViewHolder(private val binding: ItemLibraryHomeBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MyLibraryItem, position: Int) {
            binding.title.text = item.title
            binding.title.setOnClickListener {
                onLibraryItemClickListener.onLibraryItemClicked(item)
            }
            binding.detail.setOnClickListener {
                onLibraryItemClickListener.onLibraryItemDetailClicked(item)
            }

            val textColor = if (position % 2 == 0) {
                ContextCompat.getColor(itemView.context, R.color.md_black_1000)
            } else {
                ContextCompat.getColor(itemView.context, R.color.md_grey_700)
            }
            binding.title.setTextColor(textColor)

            val colorResId = if (position % 2 == 0) R.color.card_bg else R.color.dashboard_item_alternative
            val color = ContextCompat.getColor(itemView.context, colorResId)
            itemView.setBackgroundColor(color)
        }
    }

    class LibraryDiffCallback : DiffUtil.ItemCallback<MyLibraryItem>() {
        override fun areItemsTheSame(oldItem: MyLibraryItem, newItem: MyLibraryItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MyLibraryItem, newItem: MyLibraryItem): Boolean {
            return oldItem == newItem
        }
    }

    interface OnLibraryItemClickListener {
        fun onLibraryItemClicked(item: MyLibraryItem)
        fun onLibraryItemDetailClicked(item: MyLibraryItem)
    }
}