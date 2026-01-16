package org.ole.planet.myplanet.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.databinding.ItemLibraryHomeBinding
import org.ole.planet.myplanet.model.RealmMyLibrary

class LibraryListAdapter(
    private val onLibraryItemClickListener: OnLibraryItemClickListener,
    private val setTextColor: (TextView, Int) -> Unit
) : ListAdapter<RealmMyLibrary, LibraryListAdapter.LibraryViewHolder>(DIFF_CALLBACK) {

    interface OnLibraryItemClickListener {
        fun onLibraryItemClicked(library: RealmMyLibrary)
        fun onDetailClicked(library: RealmMyLibrary)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LibraryViewHolder {
        val binding = ItemLibraryHomeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LibraryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LibraryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LibraryViewHolder(private val binding: ItemLibraryHomeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.title.setOnClickListener {
                onLibraryItemClickListener.onLibraryItemClicked(getItem(adapterPosition))
            }
            binding.detail.setOnClickListener {
                onLibraryItemClickListener.onDetailClicked(getItem(adapterPosition))
            }
        }

        fun bind(item: RealmMyLibrary) {
            binding.title.text = item.title
            setTextColor(binding.title, adapterPosition)
        }
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RealmMyLibrary>() {
            override fun areItemsTheSame(
                oldItem: RealmMyLibrary,
                newItem: RealmMyLibrary
            ): Boolean {
                return oldItem._id == newItem._id
            }

            override fun areContentsTheSame(
                oldItem: RealmMyLibrary,
                newItem: RealmMyLibrary
            ): Boolean {
                return oldItem.title == newItem.title
            }
        }
    }
}
