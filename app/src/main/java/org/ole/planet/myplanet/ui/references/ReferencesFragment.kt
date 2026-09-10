package org.ole.planet.myplanet.ui.references

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseBindingFragment
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.FragmentReferenceBinding
import org.ole.planet.myplanet.model.Reference

class ReferencesFragment : BaseBindingFragment<FragmentReferenceBinding>(FragmentReferenceBinding::inflate) {
    private var homeItemClickListener: OnHomeItemClickListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        homeItemClickListener = context as? OnHomeItemClickListener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        val list = listOf(
            Reference(getString(R.string.maps), android.R.drawable.ic_dialog_map),
            Reference(getString(R.string.english_dictionary), R.drawable.ic_dictionary)
        )
        binding.rvReferences.layoutManager = GridLayoutManager(activity, 3)
        binding.rvReferences.adapter = ReferencesAdapter()
        setRecyclerAdapter(list)
        return view
    }

    private fun setRecyclerAdapter(list: List<Reference>) {
        (binding.rvReferences.adapter as ReferencesAdapter).submitList(list)
    }
}
