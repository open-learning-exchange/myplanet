package org.ole.planet.myplanet.base

import android.app.Dialog
import android.os.Bundle
import android.view.View
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentSortOptionsBinding

abstract class BaseSortFragment<T : Any> :
    BaseBindingBottomSheetFragment<FragmentSortOptionsBinding>(FragmentSortOptionsBinding::inflate) {
    protected abstract val dateSortValue: T
    protected abstract val titleSortValue: T

    protected abstract fun hasListener(): Boolean
    protected abstract fun currentSortValue(): T?
    protected abstract fun onSortSelected(value: T)
    protected open fun dateLabel(): String = getString(R.string.order_by_date)
    protected open fun titleLabel(): String = getString(R.string.order_by_title)

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
        if (!hasListener()) {
            dismissAllowingStateLoss()
            return
        }
        binding.ivCloseSort.setOnClickListener { dismiss() }

        when (currentSortValue()) {
            dateSortValue -> binding.sortOrderByDate.isChecked = true
            titleSortValue -> binding.sortOrderByTitle.isChecked = true
            else -> Unit
        }

        binding.sortOrderByDate.text = dateLabel()
        binding.sortOrderByTitle.text = titleLabel()

        binding.sortOrderByDate.setOnClickListener {
            onSortSelected(dateSortValue)
            dismiss()
        }
        binding.sortOrderByTitle.setOnClickListener {
            onSortSelected(titleSortValue)
            dismiss()
        }
    }
}
