package org.ole.planet.myplanet.ui.references

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.databinding.RowReferenceBinding
import org.ole.planet.myplanet.model.Reference

class ReferenceAdapter(
    private val references: List<Reference>,
    private val listener: OnReferenceItemClickListener
) : RecyclerView.Adapter<ReferenceAdapter.ViewHolderReference>() {

    interface OnReferenceItemClickListener {
        fun onReferenceItemClick(reference: Reference)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderReference {
        val binding = RowReferenceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderReference(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderReference, position: Int) {
        val reference = references[position]
        holder.bind(reference)
        holder.itemView.setOnClickListener {
            listener.onReferenceItemClick(reference)
        }
    }

    override fun getItemCount(): Int = references.size

    class ViewHolderReference(private val binding: RowReferenceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(reference: Reference) {
            binding.title.text = reference.title
            binding.icon.setImageResource(reference.icon)
        }
    }
}
