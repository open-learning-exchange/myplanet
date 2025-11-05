package org.ole.planet.myplanet.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemLibraryHomeBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import io.realm.RealmResults

class LibraryAdapter(
    private val libraries: RealmResults<RealmMyLibrary>,
    private val itemClickListener: (RealmMyLibrary) -> Unit,
    private val detailClickListener: (RealmMyLibrary) -> Unit
) : RecyclerView.Adapter<LibraryAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLibraryHomeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val library = libraries[position]
        holder.bind(library, position)
        holder.itemView.setOnClickListener { itemClickListener(library) }
        holder.binding.detail.setOnClickListener { detailClickListener(library) }
    }

    override fun getItemCount(): Int = libraries.size

    inner class ViewHolder(val binding: ItemLibraryHomeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(library: RealmMyLibrary, position: Int) {
            binding.title.text = library.title
            val colorResId = if (position % 2 == 0) R.color.card_bg else R.color.dashboard_item_alternative
            binding.root.setBackgroundColor(ContextCompat.getColor(itemView.context, colorResId))
        }
    }
}