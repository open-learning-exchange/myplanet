package org.ole.planet.myplanet.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemLibraryHomeBinding
import org.ole.planet.myplanet.model.RealmMyLibrary

class AdapterMyLibrary(private val listener: OnItemClickListener) : ListAdapter<RealmMyLibrary, AdapterMyLibrary.ViewHolder>(DIFF_CALLBACK) {

    interface OnItemClickListener {
        fun onItemClick(item: RealmMyLibrary)
        fun onDetailClick(item: RealmMyLibrary)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLibraryHomeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolder(private val binding: ItemLibraryHomeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RealmMyLibrary) {
            binding.title.text = item.title
            val colorResId = if (adapterPosition % 2 == 0) R.color.card_bg else R.color.dashboard_item_alternative
            val color = ContextCompat.getColor(itemView.context, colorResId)
            itemView.setBackgroundColor(color)

            binding.detail.setOnClickListener {
                listener.onDetailClick(item)
            }

            binding.title.setOnClickListener {
                listener.onItemClick(item)
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RealmMyLibrary>() {
            override fun areItemsTheSame(oldItem: RealmMyLibrary, newItem: RealmMyLibrary): Boolean {
                return oldItem._id == newItem._id
            }

            override fun areContentsTheSame(oldItem: RealmMyLibrary, newItem: RealmMyLibrary): Boolean {
                return oldItem.title == newItem.title
            }
        }
    }
}
