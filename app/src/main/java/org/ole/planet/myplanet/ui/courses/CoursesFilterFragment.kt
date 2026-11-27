package org.ole.planet.myplanet.ui.courses

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseBindingBottomSheetFragment
import org.ole.planet.myplanet.databinding.FragmentCoursesFilterBinding
import org.ole.planet.myplanet.utils.collectWhenStarted

interface CoursesFilterSheetListener {
    fun onGradeSubjectChanged(grade: String, subject: String)
    val resultCount: Flow<Int>
    fun onClearRequested()
    fun onCollectionsRequested()
    fun onShowArchivedChanged(showArchived: Boolean) {}
}

class CoursesFilterFragment : BaseBindingBottomSheetFragment<FragmentCoursesFilterBinding>(FragmentCoursesFilterBinding::inflate) {
    private var listener: CoursesFilterSheetListener? = null
    private var initialGrade: String = ""
    private var initialSubject: String = ""
    private var initialShowArchived: Boolean = false

    fun setListener(listener: CoursesFilterSheetListener) {
        this.listener = listener
    }

    fun setInitialSelection(grade: String, subject: String, showArchived: Boolean = false) {
        initialGrade = grade
        initialSubject = subject
        initialShowArchived = showArchived
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
        val currentListener = listener
        if (currentListener == null) {
            dismissAllowingStateLoss()
            return
        }
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
        binding.chkArchived.isChecked = initialShowArchived
        binding.chkArchived.setOnCheckedChangeListener { _, isChecked ->
            listener?.onShowArchivedChanged(isChecked)
        }

        collectWhenStarted(currentListener.resultCount) { count ->
            binding.btnConfirmFilters.text = getString(R.string.show_n_results, count)
        }
    }

    private fun setupSpinner(spinner: Spinner, arrayRes: Int, initialValue: String) {
        val ctx = requireContext()
        val adapter = ArrayAdapter.createFromResource(ctx, arrayRes, R.layout.spinner_item)
        adapter.setDropDownViewResource(R.layout.custom_simple_list_item_1)
        spinner.adapter = adapter
        if (initialValue.isNotEmpty()) {
            val index = filterValues(ctx, arrayRes).indexOf(initialValue)
                .takeIf { it >= 0 } ?: resources.getStringArray(arrayRes).indexOf(initialValue)
            if (index >= 0) spinner.setSelection(index)
        }
    }

    private fun selectedValue(spinner: Spinner, arrayRes: Int): String {
        val position = spinner.selectedItemPosition
        return if (position <= 0) "" else filterValues(requireContext(), arrayRes).getOrElse(position) { "" }
    }

    private fun notifyChange() {
        listener?.onGradeSubjectChanged(
            selectedValue(binding.spnGrade, R.array.grade_level),
            selectedValue(binding.spnSubject, R.array.subject_level)
        )
    }

    companion object {
        fun filterValues(context: Context, arrayRes: Int): Array<String> =
            if (arrayRes == R.array.grade_level) {
                context.resources.getStringArray(R.array.grade_level_values)
            } else {
                englishValues(context, arrayRes)
            }

        fun englishValues(context: Context, arrayRes: Int): Array<String> {
            val english = Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
            return context.createConfigurationContext(english).resources.getStringArray(arrayRes)
        }
    }
}
