package org.ole.planet.myplanet.ui.references

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.FragmentReferenceBinding
import org.ole.planet.myplanet.databinding.RowReferenceBinding
import org.ole.planet.myplanet.model.Reference
import org.ole.planet.myplanet.ui.dictionary.DictionaryActivity
import org.ole.planet.myplanet.ui.maps.OfflineMapsActivity

class ReferencesFragment : Fragment() {
    private var _binding: FragmentReferenceBinding? = null
    private val binding get() = _binding!!
    private var homeItemClickListener: OnHomeItemClickListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        homeItemClickListener = context as? OnHomeItemClickListener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReferenceBinding.inflate(inflater, container, false)
        val list = listOf(
            Reference(getString(R.string.maps), android.R.drawable.ic_dialog_map),
            Reference(getString(R.string.english_dictionary), R.drawable.ic_dictionary)
        )
        binding.rvReferences.layoutManager = GridLayoutManager(activity, 3)
        binding.rvReferences.adapter = ReferenceAdapter()
        setRecyclerAdapter(list)
        return binding.root
    }

    private fun setRecyclerAdapter(list: List<Reference>) {
        (binding.rvReferences.adapter as ReferenceAdapter).submitList(list)
    }

    inner class ReferenceAdapter : ListAdapter<Reference, ViewHolderReference>(ReferenceDiffCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderReference {
            val rowReferenceBinding = RowReferenceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolderReference(rowReferenceBinding)
        }

        override fun onBindViewHolder(holder: ViewHolderReference, position: Int) {
            val reference = getItem(position)
            holder.rowReferenceBinding.title.text = reference.title
            holder.rowReferenceBinding.icon.setImageResource(reference.icon)
            holder.rowReferenceBinding.root.setOnClickListener {
                if (holder.bindingAdapterPosition == 0)
                    startActivity(Intent(activity, OfflineMapsActivity::class.java))
                else {
                    startActivity(Intent(activity, DictionaryActivity::class.java))
                }
            }
        }
    }

    class ReferenceDiffCallback : DiffUtil.ItemCallback<Reference>() {
        override fun areItemsTheSame(oldItem: Reference, newItem: Reference): Boolean {
            return oldItem.title == newItem.title
        }

        override fun areContentsTheSame(oldItem: Reference, newItem: Reference): Boolean {
            return oldItem == newItem
        }
    }

    class ViewHolderReference(val rowReferenceBinding: RowReferenceBinding) : RecyclerView.ViewHolder(rowReferenceBinding.root)

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
