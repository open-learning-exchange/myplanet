package org.ole.planet.myplanet.ui.references

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.databinding.RowReferenceBinding
import org.ole.planet.myplanet.model.Reference
import org.ole.planet.myplanet.utilities.DiffUtils

class ReferencesAdapter(private val onReferenceClicked: (Reference) -> Unit) :
    ListAdapter<Reference, ReferencesAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RowReferenceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val reference = getItem(position)
        holder.bind(reference)
        holder.itemView.setOnClickListener { onReferenceClicked(reference) }
    }

    class ViewHolder(private val binding: RowReferenceBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(reference: Reference) {
            binding.title.text = reference.title
            binding.icon.setImageResource(reference.icon)
        }
    }

    companion object {
        val DIFF_CALLBACK = DiffUtils.itemCallback<Reference>(
            areItemsTheSame = { oldItem, newItem -> oldItem.title == newItem.title },
            areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
        )
    }
}
