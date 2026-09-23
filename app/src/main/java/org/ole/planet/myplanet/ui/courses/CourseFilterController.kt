package org.ole.planet.myplanet.ui.courses

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
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
    private lateinit var tvSelected: TextView
    private var layoutSearch: View? = null
    private var scrollChipFilter: View? = null
    private var layoutViewToggle: View? = null
    private var progressFilter: String = ""
    private var grade: String = ""
    private var subject: String = ""
    val searchTags: MutableList<TagEntity> = ArrayList()
    private var searchTextWatcher: TextWatcher? = null
    private var searchJob: Job? = null

    fun setup() {
        etSearch = rootView.findViewById(R.id.et_search)
        tvSelected = rootView.findViewById(R.id.tv_selected)
        layoutSearch = rootView.findViewById(R.id.layout_search) ?: (etSearch.parent as? View)
        scrollChipFilter = rootView.findViewById(R.id.scroll_chip_filter) ?: (rootView.findViewById<View>(R.id.chip_filter_row)?.parent as? View)
        layoutViewToggle = rootView.findViewById(R.id.layout_view_toggle) ?: (rootView.findViewById<View>(R.id.toggle_grid)?.parent as? View)
        setupSearchWatcher()
    }

    fun setProgressFilter(value: String) {
        progressFilter = value
        _filterState.value = currentState()
        onScrollToTop()
    }

    fun currentGrade(): String = grade
    fun currentSubject(): String = subject

    fun setGradeSubject(newGrade: String, newSubject: String) {
        grade = newGrade
        subject = newSubject
        _filterState.value = currentState()
        onScrollToTop()
    }

    fun restoreFilterState(state: FilterState) {
        restoreSearchText(state.searchText)
        grade = state.grade
        subject = state.subject
        restoreTags(state.tags, state.tagNames)
        progressFilter = state.progressFilter
        if (::tvSelected.isInitialized) {
            refreshTagText()
        }
        _filterState.value = currentState()
    }

    private fun restoreSearchText(searchText: String) {
        if (::etSearch.isInitialized && etSearch.text.toString() != searchText) {
            etSearch.setText(searchText)
        }
    }

    private fun restoreTags(tags: List<TagEntity>, tagNames: List<String>) {
        searchTags.clear()
        if (tags.isNotEmpty()) {
            tags.forEach { addTagInternal(it) }
        } else {
            tagNames.forEach { name ->
                addTagInternal(TagEntity().apply { this.name = name })
            }
        }
    }

    private fun addTagInternal(tag: TagEntity) {
        if (!searchTags.any { it.matches(tag) }) {
            searchTags.add(tag)
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

    fun addTag(tag: TagEntity) {
        addTagInternal(tag)
        _filterState.value = currentState()
        refreshTagText()
        onScrollToTop()
    }

    fun setTags(list: List<TagEntity>) {
        searchTags.clear()
        list.forEach { addTagInternal(it) }
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
        grade = ""
        subject = ""
        progressFilter = ""
        _filterState.value = currentState()
        onScrollToTop()
    }

    fun filterApplied(): Boolean = currentState().isActive

    fun currentState(): FilterState {
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
        rootView.findViewById<View>(R.id.sort_filter_capsule)?.visibility = visibility
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
    }
}
