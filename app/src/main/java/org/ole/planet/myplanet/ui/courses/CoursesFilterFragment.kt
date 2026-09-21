package org.ole.planet.myplanet.ui.courses

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseBindingBottomSheetFragment
import org.ole.planet.myplanet.databinding.FragmentCoursesFilterBinding

interface CoursesFilterSheetListener {
    fun onGradeSubjectChanged(grade: String, subject: String)
    suspend fun getFilteredCount(grade: String, subject: String): Int
    fun onClearRequested()
    fun onCollectionsRequested()
}

class CoursesFilterFragment : BaseBindingBottomSheetFragment<FragmentCoursesFilterBinding>(FragmentCoursesFilterBinding::inflate) {
    private var listener: CoursesFilterSheetListener? = null
    private var initialGrade: String = ""
    private var initialSubject: String = ""

    fun setListener(listener: CoursesFilterSheetListener) {
        this.listener = listener
    }

    fun setInitialSelection(grade: String, subject: String) {
        initialGrade = grade
        initialSubject = subject
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
        val view = super.onCreateView(inflater, container, savedInstanceState)
        binding.ivClose.setOnClickListener { dismiss() }
        binding.btnClearTags.setOnClickListener {
            listener?.onClearRequested()
            dismiss()
        }
        binding.btnConfirmFilters.setOnClickListener { dismiss() }
        binding.btnCollections.setOnClickListener {
            listener?.onCollectionsRequested()
            dismiss()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSpinner(binding.spnGrade, R.array.grade_level, initialGrade)
        setupSpinner(binding.spnSubject, R.array.subject_level, initialSubject)

        val spinnerListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, itemView: View?, position: Int, id: Long) {
                notifyChange()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.spnGrade.onItemSelectedListener = spinnerListener
        binding.spnSubject.onItemSelectedListener = spinnerListener

        updateResultCount()
    }

    private fun setupSpinner(spinner: Spinner, arrayRes: Int, initialValue: String) {
        val ctx = requireContext()
        val adapter = ArrayAdapter.createFromResource(ctx, arrayRes, R.layout.spinner_item)
        adapter.setDropDownViewResource(R.layout.custom_simple_list_item_1)
        spinner.adapter = adapter
        if (initialValue.isNotEmpty()) {
            val values = resources.getStringArray(arrayRes)
            val index = values.indexOf(initialValue)
            if (index >= 0) spinner.setSelection(index)
        }
    }

    private fun selectedGrade(): String = binding.spnGrade.selectedItem?.toString()?.takeIf { it != "All" } ?: ""
    private fun selectedSubject(): String = binding.spnSubject.selectedItem?.toString()?.takeIf { it != "All" } ?: ""

    private fun notifyChange() {
        listener?.onGradeSubjectChanged(selectedGrade(), selectedSubject())
        updateResultCount()
    }

    private fun updateResultCount() {
        val currentListener = listener ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val count = currentListener.getFilteredCount(selectedGrade(), selectedSubject())
            if (_binding != null) {
                binding.btnConfirmFilters.text = getString(R.string.show_n_results, count)
            }
        }
    }
}
