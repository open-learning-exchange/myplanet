package org.ole.planet.myplanet.ui.resources

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseBindingBottomSheetFragment
import org.ole.planet.myplanet.databinding.FragmentResourcesSortBinding

class ResourcesSortFragment : BaseBindingBottomSheetFragment<FragmentResourcesSortBinding>(FragmentResourcesSortBinding::inflate) {
    fun interface SortSelectionListener {
        fun onSortSelected(mode: ResourcesViewModel.SortMode)
    }

    private var listener: SortSelectionListener? = null
    private var currentMode: ResourcesViewModel.SortMode = ResourcesViewModel.SortMode.NONE
    private var isDateAscending: Boolean = true
    private var isTitleAscending: Boolean = false

    fun setListener(listener: SortSelectionListener) {
        this.listener = listener
    }

    fun setCurrentMode(mode: ResourcesViewModel.SortMode) {
        currentMode = mode
    }

    fun setCurrentDirection(isDateAscending: Boolean, isTitleAscending: Boolean) {
        this.isDateAscending = isDateAscending
        this.isTitleAscending = isTitleAscending
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.ivCloseSort.setOnClickListener { dismiss() }

        when (currentMode) {
            ResourcesViewModel.SortMode.DATE -> binding.sortOrderByDate.isChecked = true
            ResourcesViewModel.SortMode.TITLE -> binding.sortOrderByTitle.isChecked = true
            ResourcesViewModel.SortMode.NONE -> Unit
        }

        updateDirectionLabels()

        binding.sortOrderByDate.setOnClickListener {
            listener?.onSortSelected(ResourcesViewModel.SortMode.DATE)
            dismiss()
        }
        binding.sortOrderByTitle.setOnClickListener {
            listener?.onSortSelected(ResourcesViewModel.SortMode.TITLE)
            dismiss()
        }
    }

    private fun updateDirectionLabels() {
        val dateArrow = if (isDateAscending) "↑" else "↓"
        val titleArrow = if (isTitleAscending) "↑" else "↓"
        binding.sortOrderByDate.text = "${getString(R.string.order_by_date)} $dateArrow"
        binding.sortOrderByTitle.text = "${getString(R.string.order_by_title)} $titleArrow"
    }

}
