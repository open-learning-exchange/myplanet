package org.ole.planet.myplanet.ui.courses

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.TagEntity

data class FilterState(
    val searchText: String,
    val grade: String,
    val subject: String,
    val tagNames: List<String>,
    val progressFilter: String = "",
    val tags: List<TagEntity> = emptyList()
) {
    val isActive: Boolean
        get() = searchText.isNotEmpty() || grade.isNotEmpty() || subject.isNotEmpty() || tagNames.isNotEmpty() || progressFilter.isNotEmpty()
}

class CourseFilterController(
    private val rootView: View,
    private val coroutineScope: CoroutineScope,
    private val onScrollToTop: () -> Unit
) {
    private val _filterState = MutableStateFlow(FilterState("", "", "", emptyList()))
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    private lateinit var etSearch: EditText
    private lateinit var spnGrade: Spinner
    private lateinit var spnSubject: Spinner
    private lateinit var tvSelected: TextView
    private var layoutSearch: View? = null
    private var scrollChipFilter: View? = null
    private var layoutViewToggle: View? = null
    private var progressFilter: String = ""
    val searchTags: MutableList<TagEntity> = ArrayList()
    private var searchTextWatcher: TextWatcher? = null
    private var spinnerListener: AdapterView.OnItemSelectedListener? = null
    private var searchJob: Job? = null

    fun setup() {
        etSearch = rootView.findViewById(R.id.et_search)
        spnGrade = rootView.findViewById(R.id.spn_grade)
        spnSubject = rootView.findViewById(R.id.spn_subject)
        tvSelected = rootView.findViewById(R.id.tv_selected)
        layoutSearch = rootView.findViewById(R.id.layout_search) ?: (etSearch.parent as? View)
        scrollChipFilter = rootView.findViewById(R.id.scroll_chip_filter) ?: (rootView.findViewById<View>(R.id.chip_filter_row)?.parent as? View)
        layoutViewToggle = rootView.findViewById(R.id.layout_view_toggle) ?: (rootView.findViewById<View>(R.id.toggle_grid)?.parent as? View)
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
        spnSubject.adapter = subjectAdapter

        spinnerListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, i: Int, l: Long) {
                if (view == null) return
                _filterState.value = currentState()
                onScrollToTop()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spnGrade.onItemSelectedListener = spinnerListener
        spnSubject.onItemSelectedListener = spinnerListener
    }

    fun setProgressFilter(value: String) {
        progressFilter = value
        _filterState.value = currentState()
        onScrollToTop()
    }

    fun restoreFilterState(state: FilterState, availableTags: List<TagEntity> = emptyList()) {
        val listener = spinnerListener
        if (::spnGrade.isInitialized) spnGrade.onItemSelectedListener = null
        if (::spnSubject.isInitialized) spnSubject.onItemSelectedListener = null

        restoreSearchText(state.searchText)
        if (::spnGrade.isInitialized) {
            restoreSpinnerSelection(spnGrade, state.grade)
        }
        if (::spnSubject.isInitialized) {
            restoreSpinnerSelection(spnSubject, state.subject)
        }
        restoreTags(state.tags, state.tagNames, availableTags)
        progressFilter = state.progressFilter
        if (::tvSelected.isInitialized) {
            refreshTagText()
        }

        if (::spnGrade.isInitialized) spnGrade.onItemSelectedListener = listener
        if (::spnSubject.isInitialized) spnSubject.onItemSelectedListener = listener

        _filterState.value = currentState()
    }

    private fun restoreSearchText(searchText: String) {
        if (::etSearch.isInitialized && etSearch.text.toString() != searchText) {
            etSearch.setText(searchText)
        }
    }

    private fun restoreSpinnerSelection(spinner: Spinner, targetValue: String) {
        val adapter = spinner.adapter ?: return
        for (i in 0 until adapter.count) {
            val itemStr = adapter.getItem(i).toString()
            if (itemStr == targetValue || (targetValue.isEmpty() && i == 0)) {
                spinner.setSelection(i)
                break
            }
        }
    }

    private fun restoreTags(tags: List<TagEntity>, tagNames: List<String>, availableTags: List<TagEntity>) {
        searchTags.clear()
        val seenNames = HashSet<String>()
        if (tags.isNotEmpty()) {
            tags.forEach { tag ->
                val name = tag.name
                if (name != null && seenNames.add(name)) {
                    searchTags.add(tag)
                }
            }
        } else {
            tagNames.forEach { name ->
                if (seenNames.add(name)) {
                    val matchedTag = availableTags.find { it.name == name }
                        ?: TagEntity().apply { this.name = name }
                    searchTags.add(matchedTag)
                }
            }
        }
    }

    private fun setupSearchWatcher() {
        searchTextWatcher = object : TextWatcher {
            @Suppress("EmptyMethod")
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (!etSearch.isFocused) return
                searchJob?.cancel()
                searchJob = coroutineScope.launch {
                    delay(300)
                    _filterState.value = currentState()
                }
            }
            @Suppress("EmptyMethod")
            override fun afterTextChanged(s: Editable) {}
        }
        etSearch.addTextChangedListener(searchTextWatcher)
    }

    private fun setupClearTagsButton() {
        rootView.findViewById<View>(R.id.btn_clear_tags).setOnClickListener { clearAll() }
    }

    fun addTag(tag: TagEntity) {
        if (!searchTags.any { it.name == tag.name }) searchTags.add(tag)
        _filterState.value = currentState()
        refreshTagText()
        onScrollToTop()
    }

    fun setTags(list: List<TagEntity>) {
        searchTags.clear()
        val seenNames = HashSet<String?>()
        list.forEach { tag ->
            if (seenNames.add(tag.name)) {
                searchTags.add(tag)
            }
        }
        _filterState.value = currentState()
        onScrollToTop()
    }

    fun setSingleTag(tag: TagEntity) {
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
        progressFilter = ""
        _filterState.value = currentState()
        onScrollToTop()
    }

    fun filterApplied(): Boolean = currentState().isActive

    fun currentState(): FilterState {
        val grade = spnGrade.selectedItem?.toString()?.takeIf { it != "All" } ?: ""
        val subject = spnSubject.selectedItem?.toString()?.takeIf { it != "All" } ?: ""
        return FilterState(
            searchText = etSearch.text.toString().trim(),
            grade = grade,
            subject = subject,
            tagNames = searchTags.mapNotNull { it.name },
            progressFilter = progressFilter,
            tags = searchTags.toList()
        )
    }

    fun setListVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        layoutSearch?.visibility = visibility
        if (layoutSearch == null) {
            etSearch.visibility = visibility
        }
        scrollChipFilter?.visibility = visibility
        if (scrollChipFilter == null) {
            rootView.findViewById<View>(R.id.chip_filter_row)?.visibility = visibility
        }
        layoutViewToggle?.visibility = visibility
        if (layoutViewToggle == null) {
            rootView.findViewById<View>(R.id.toggle_grid)?.visibility = visibility
            rootView.findViewById<View>(R.id.toggle_list)?.visibility = visibility
        }
        rootView.findViewById<View>(R.id.filter)?.visibility = visibility
        if (!visible) tvSelected.visibility = View.GONE
    }

    private fun refreshTagText() {
        tvSelected.text = searchTags.joinToString(
            separator = ",",
            prefix = tvSelected.context.getString(R.string.selected)
        ) { it.name.orEmpty() }
    }

    fun detach() {
        searchJob?.cancel()
        searchTextWatcher?.let { etSearch.removeTextChangedListener(it) }
        searchTextWatcher = null
        spnGrade.onItemSelectedListener = null
        spnSubject.onItemSelectedListener = null
        spinnerListener = null
    }
}
