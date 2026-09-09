package org.ole.planet.myplanet.ui.resources

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.ole.planet.myplanet.databinding.FragmentResourcesSortBinding

class ResourcesSortFragment : BottomSheetDialogFragment() {
    fun interface SortSelectionListener {
        fun onSortSelected(mode: ResourcesViewModel.SortMode)
    }

    private var _binding: FragmentResourcesSortBinding? = null
    private val binding get() = _binding!!
    private var listener: SortSelectionListener? = null
    private var currentMode: ResourcesViewModel.SortMode = ResourcesViewModel.SortMode.NONE

    fun setListener(listener: SortSelectionListener) {
        this.listener = listener
    }

    fun setCurrentMode(mode: ResourcesViewModel.SortMode) {
        currentMode = mode
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentResourcesSortBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.ivCloseSort.setOnClickListener { dismiss() }

        when (currentMode) {
            ResourcesViewModel.SortMode.DATE -> binding.sortOrderByDate.isChecked = true
            ResourcesViewModel.SortMode.TITLE -> binding.sortOrderByTitle.isChecked = true
            ResourcesViewModel.SortMode.NONE -> Unit
        }

        binding.sortOrderByDate.setOnClickListener {
            listener?.onSortSelected(ResourcesViewModel.SortMode.DATE)
            dismiss()
        }
        binding.sortOrderByTitle.setOnClickListener {
            listener?.onSortSelected(ResourcesViewModel.SortMode.TITLE)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
