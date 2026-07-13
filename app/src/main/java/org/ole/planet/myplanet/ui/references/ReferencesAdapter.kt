package org.ole.planet.myplanet.ui.references

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.databinding.RowReferenceBinding
import org.ole.planet.myplanet.model.Reference
import org.ole.planet.myplanet.ui.dictionary.DictionaryActivity
import org.ole.planet.myplanet.ui.maps.OfflineMapsActivity
import org.ole.planet.myplanet.utils.DiffUtils

class ReferencesAdapter : ListAdapter<Reference, ViewHolderReference>(
    DiffUtils.itemCallback<Reference>(
        { oldItem, newItem -> oldItem.title == newItem.title },
        { oldItem, newItem -> oldItem == newItem }
    )
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderReference {
        val rowReferenceBinding = RowReferenceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderReference(rowReferenceBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderReference, position: Int) {
        val reference = getItem(position)
        holder.rowReferenceBinding.title.text = reference.title
        holder.rowReferenceBinding.icon.setImageResource(reference.icon)
        holder.rowReferenceBinding.root.setOnClickListener {
            val context = holder.rowReferenceBinding.root.context
            if (holder.bindingAdapterPosition == 0)
                context.startActivity(Intent(context, OfflineMapsActivity::class.java))
            else {
                context.startActivity(Intent(context, DictionaryActivity::class.java))
            }
        }
    }
}

class ViewHolderReference(val rowReferenceBinding: RowReferenceBinding) : RecyclerView.ViewHolder(rowReferenceBinding.root)
