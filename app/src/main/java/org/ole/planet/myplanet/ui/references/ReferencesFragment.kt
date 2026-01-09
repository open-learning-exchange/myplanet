package org.ole.planet.myplanet.ui.references

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
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
    private lateinit var referencesAdapter: ReferencesAdapter

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
        setupRecyclerView(list)
        return binding.root
    }

    private fun setupRecyclerView(list: List<Reference>) {
        referencesAdapter = ReferencesAdapter { reference ->
            if (reference.title == getString(R.string.maps)) {
                startActivity(Intent(activity, OfflineMapsActivity::class.java))
            } else if (reference.title == getString(R.string.english_dictionary)) {
                startActivity(Intent(activity, DictionaryActivity::class.java))
            }
        }
        binding.rvReferences.adapter = referencesAdapter
        referencesAdapter.submitList(list)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
