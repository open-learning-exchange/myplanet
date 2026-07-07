package org.ole.planet.myplanet.ui.courses

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmTag

data class FilterState(
    val searchText: String,
    val grade: String,
    val subject: String,
    val tagNames: List<String>,
    val progressFilter: String = ""
) {
    val isActive: Boolean
        get() = searchText.isNotEmpty() || grade.isNotEmpty() || subject.isNotEmpty() || tagNames.isNotEmpty() || progressFilter.isNotEmpty()
}

class CourseFilterController(
    private val rootView: View,
    private val onScrollToTop: () -> Unit
) {
    private val _filterState = MutableStateFlow(FilterState("", "", "", emptyList()))
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    private lateinit var etSearch: EditText
    private lateinit var spnGrade: Spinner
    private lateinit var spnSubject: Spinner
    private lateinit var spnProgress: Spinner
    private lateinit var tvSelected: TextView
    val searchTags: MutableList<RealmTag> = ArrayList()
    private var searchTextWatcher: TextWatcher? = null

    fun setup() {
        etSearch = rootView.findViewById(R.id.et_search)
        spnGrade = rootView.findViewById(R.id.spn_grade)
        spnSubject = rootView.findViewById(R.id.spn_subject)
        spnProgress = rootView.findViewById(R.id.spn_progress)
        tvSelected = rootView.findViewById(R.id.tv_selected)
        setupSpinners()
        setupSearchWatcher()
        setupClearTagsButton()
    }

    private fun setupSpinners() {
        val ctx = rootView.context
        val gradeAdapter = ArrayAdapter.createFromResource(ctx, R.array.grade_level, R.layout.spinner_item)
        gradeAdapter.setDropDownViewResource(R.layout.custom_simple_list_item_1)
        spnGrade.adapter = gradeAdapter

        val subjectAdapter = ArrayAdapter.createFromResource(ctx, R.array.subject_level, R.layout.spinner_item)
        subjectAdapter.setDropDownViewResource(R.layout.custom_simple_list_item_1)

        val progressAdapter = ArrayAdapter.createFromResource(ctx, R.array.progress_filter, R.layout.spinner_item)
        progressAdapter.setDropDownViewResource(R.layout.custom_simple_list_item_1)
        spnProgress.adapter = progressAdapter
        spnSubject.adapter = subjectAdapter

        val spinnerListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, i: Int, l: Long) {
                if (view == null) return
                _filterState.value = currentState()
                onScrollToTop()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spnGrade.onItemSelectedListener = spinnerListener
        spnSubject.onItemSelectedListener = spinnerListener
        spnProgress.onItemSelectedListener = spinnerListener
    }

    private fun setupSearchWatcher() {
        searchTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (!etSearch.isFocused) return
                _filterState.value = currentState()
            }
            override fun afterTextChanged(s: Editable) {}
        }
        etSearch.addTextChangedListener(searchTextWatcher)
    }

    private fun setupClearTagsButton() {
        rootView.findViewById<View>(R.id.btn_clear_tags).setOnClickListener { clearAll() }
    }

    fun addTag(tag: RealmTag) {
        if (!searchTags.any { it.name == tag.name }) searchTags.add(tag)
        _filterState.value = currentState()
        refreshTagText()
        onScrollToTop()
    }

    fun setTags(list: List<RealmTag>) {
        searchTags.clear()
        list.forEach { tag -> if (!searchTags.any { it.name == tag.name }) searchTags.add(tag) }
        _filterState.value = currentState()
        onScrollToTop()
    }

    fun setSingleTag(tag: RealmTag) {
        searchTags.clear()
        searchTags.add(tag)
        tvSelected.text = tvSelected.context.getString(R.string.tag_selected, tag.name)
        _filterState.value = currentState()
        onScrollToTop()
    }

    fun clearAll() {
        searchTags.clear()
        etSearch.setText("")
        tvSelected.text = ""
        spnGrade.setSelection(0)
        spnSubject.setSelection(0)
        spnProgress.setSelection(0)
        _filterState.value = currentState()
        onScrollToTop()
    }

    fun filterApplied(): Boolean = currentState().isActive

    fun currentState(): FilterState {
        val grade = spnGrade.selectedItem?.toString()?.takeIf { it != "All" } ?: ""
        val subject = spnSubject.selectedItem?.toString()?.takeIf { it != "All" } ?: ""
        val progress = spnProgress.selectedItem?.toString()?.takeIf { it != "All" } ?: ""
        return FilterState(
            searchText = etSearch.text.toString().trim(),
            grade = grade,
            subject = subject,
            tagNames = searchTags.mapNotNull { it.name },
            progressFilter = progress
        )
    }

    fun setListVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        etSearch.visibility = visibility
        rootView.findViewById<View>(R.id.filter).visibility = visibility
        if (!visible) tvSelected.visibility = View.GONE
    }

    private fun refreshTagText() {
        tvSelected.text = searchTags.joinToString(
            separator = ",",
            prefix = tvSelected.context.getString(R.string.selected)
        ) { it.name.orEmpty() }
    }

    fun detach() {
        searchTextWatcher?.let { etSearch.removeTextChangedListener(it) }
        searchTextWatcher = null

    }
}
