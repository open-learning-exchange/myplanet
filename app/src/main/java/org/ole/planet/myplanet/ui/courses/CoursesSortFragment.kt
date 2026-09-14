package org.ole.planet.myplanet.ui.courses

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.ole.planet.myplanet.databinding.FragmentCoursesSortBinding

class CoursesSortFragment : BottomSheetDialogFragment() {
    fun interface SortSelectionListener {
        fun onSortSelected(type: CoursesViewModel.SortType)
    }

    private var _binding: FragmentCoursesSortBinding? = null
    private val binding get() = _binding!!
    private var listener: SortSelectionListener? = null
    private var currentType: CoursesViewModel.SortType? = null

    fun setListener(listener: SortSelectionListener) {
        this.listener = listener
    }

    fun setCurrentType(type: CoursesViewModel.SortType?) {
        currentType = type
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
        _binding = FragmentCoursesSortBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.ivCloseSort.setOnClickListener { dismiss() }

        when (currentType) {
            CoursesViewModel.SortType.DATE -> binding.sortOrderByDate.isChecked = true
            CoursesViewModel.SortType.TITLE -> binding.sortOrderByTitle.isChecked = true
            null -> Unit
        }

        binding.sortOrderByDate.setOnClickListener {
            listener?.onSortSelected(CoursesViewModel.SortType.DATE)
            dismiss()
        }
        binding.sortOrderByTitle.setOnClickListener {
            listener?.onSortSelected(CoursesViewModel.SortType.TITLE)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
