package org.ole.planet.myplanet.ui.courses

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.callback.OnCourseItemSelectedListener
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.callback.OnTagClickListener
import org.ole.planet.myplanet.model.Course
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.model.Tag
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.services.sync.SyncManager
import org.ole.planet.myplanet.ui.components.FragmentNavigator
import org.ole.planet.myplanet.ui.resources.CollectionsFragment
import org.ole.planet.myplanet.ui.sync.RealtimeSyncHelper
import org.ole.planet.myplanet.ui.sync.RealtimeSyncMixin
import org.ole.planet.myplanet.utils.DialogUtils
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.KeyboardUtils.setupUI

@AndroidEntryPoint
class CoursesFragment : BaseRecyclerFragment<RealmMyCourse?>(), OnCourseItemSelectedListener, OnTagClickListener, RealtimeSyncMixin {
    private lateinit var tvAddToLib: TextView
    private lateinit var tvSelected: TextView
    private lateinit var etSearch: EditText
    private lateinit var adapterCourses: CoursesAdapter
    private lateinit var btnRemove: Button
    private lateinit var btnArchive: Button
    private lateinit var orderByDate: Button
    private lateinit var orderByTitle: Button
    private lateinit var selectAll: CheckBox
    var userModel: RealmUser ?= null
    lateinit var spnGrade: Spinner
    lateinit var spnSubject: Spinner
    lateinit var searchTags: MutableList<RealmTag>
    private lateinit var confirmation: AlertDialog
    private var isUpdatingSelectAllState = false
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null
    private var searchTextWatcher: TextWatcher? = null
    private var searchJob: Job? = null
    private var selectionJob: Job? = null
    private val viewModel: CoursesViewModel by viewModels()

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var prefManager: SharedPrefManager

    @Inject
    lateinit var userSessionManager: UserSessionManager

    private val serverUrl: String
        get() = prefManager.getServerUrl()

    private lateinit var realtimeSyncHelper: RealtimeSyncHelper

    override fun getLayout(): Int {
        return R.layout.fragment_my_course
    }

    private fun scrollToTop() {
        recyclerView.post {
            if ((recyclerView.adapter?.itemCount ?: 0) > 0) {
                recyclerView.scrollToPosition(0)
            }
        }
    }


    private fun loadDataAsync() {
        val hostActivity = activity ?: return
        if (hostActivity.isFinishing) return
        viewModel.loadCourses(isMyCourseLib, model?.id)
    }

    override suspend fun getAdapter(): RecyclerView.Adapter<out RecyclerView.ViewHolder> {
        val hostActivity = activity ?: throw CancellationException("Fragment detached")

        if (userModel == null) {
            userModel = userSessionManager.getUserModel()
        }

        adapterCourses = CoursesAdapter(
            hostActivity,
            HashMap(),
            userModel?.isGuest() ?: true,
            isMyCourseLib
        )

        adapterCourses.setListener(this@CoursesFragment)
        adapterCourses.setRatingChangeListener(this@CoursesFragment)
        enableSortButtons()
        viewModel.loadCourses(isMyCourseLib, model?.id)
        return adapterCourses
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(requireView().findViewById(R.id.my_course_parent_layout), requireActivity())
        additionalSetup()
        setupMyProgressButton()
        viewLifecycleOwner.lifecycleScope.launch {
            userModel = userSessionManager.getUserModel()
            model = userModel
            searchTags = ArrayList()
            initializeView()
            setupButtonVisibility()
            setupEventListeners()
            clearTags()
            if (!isMyCourseLib) tvFragmentInfo.setText(R.string.our_courses)
            if (::adapterCourses.isInitialized) {
                showNoData(tvMessage, adapterCourses.itemCount, "courses")
            }
            updateCheckBoxState(false)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.coursesState.collectLatest { state ->
                if (!::adapterCourses.isInitialized) return@collectLatest

                if (isMyCourseLib) {
                    val courseIds = state.courses.mapNotNull { it.courseId }
                    resources = coursesRepository.getCourseOfflineResources(courseIds)
                    courseLib = "courses"
                }

                val courses = state.courses
                adapterCourses.setProgressMap(state.progressMap)
                adapterCourses.setRatingMap(state.map)
                adapterCourses.setTagsMap(state.tagsMap)
                adapterCourses.submitList(courses) {
                    if (isAdded && ::selectAll.isInitialized) {
                        selectedItems?.clear()
                        clearAllSelections()
                        checkList()
                        showNoData(tvMessage, courses.size, "courses")
                    }
                }
            }
        }


        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.syncStatus.collectLatest { status ->
                when (status) {
                    is SyncStatus.Idle -> { /* Do nothing */ }
                    is SyncStatus.Syncing -> {
                        if (isAdded && !requireActivity().isFinishing) {
                            if (customProgressDialog == null) {
                                customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
                            }
                            customProgressDialog?.setText(getString(R.string.syncing_courses_data))
                            customProgressDialog?.show()
                        }
                    }
                    is SyncStatus.Success -> {
                        if (isAdded) {
                            customProgressDialog?.setText(getString(R.string.loading_courses))
                            delay(3000)
                            customProgressDialog?.dismiss()
                            customProgressDialog = null
                            loadDataAsync()
                            prefManager.setSynced(SharedPrefManager.SyncKey.COURSES, true)
                            viewModel.resetSyncStatus()
                        }
                    }
                    is SyncStatus.Failed -> {
                        if (isAdded) {
                            customProgressDialog?.dismiss()
                            customProgressDialog = null
                            Snackbar.make(requireView(), "Sync failed: ${status.message ?: "Unknown error"}", Snackbar.LENGTH_LONG)
                                .setAction("Retry") {
                                    viewModel.startCoursesSync()
                                }.show()
                            viewModel.resetSyncStatus()
                        }
                    }
                }
            }
        }

        realtimeSyncHelper = RealtimeSyncHelper(this, this)
        realtimeSyncHelper.setupRealtimeSync()
        viewModel.startCoursesSync()
    }

    private fun setupButtonVisibility() {
        if (isMyCourseLib) {
            btnRemove.visibility = View.VISIBLE
            btnArchive.visibility = View.VISIBLE
            checkList()
        } else {
            btnRemove.visibility = View.GONE
            btnArchive.visibility = View.GONE
        }
        hideButtons()
    }

    private fun setupEventListeners() {
        searchTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (!etSearch.isFocused) return
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300)
                    filterCoursesAndUpdateUi()
                }
            }
            override fun afterTextChanged(s: Editable) {}
        }
        etSearch.addTextChangedListener(searchTextWatcher)

        btnRemove.setOnClickListener {
            val alertDialogBuilder = AlertDialog.Builder(ContextThemeWrapper(this.context, R.style.CustomAlertDialog))
            val message = if (countSelected() == 1) {
                R.string.are_you_sure_you_want_to_leave_this_course
            } else {
                R.string.are_you_sure_you_want_to_leave_these_courses
            }
            alertDialogBuilder.setMessage(message)
                .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                    val courseIdsToRemove = selectedItems?.mapNotNull { it?.courseId } ?: emptyList()
                    viewLifecycleOwner.lifecycleScope.launch {
                        deleteSelected(true)
                        clearAllSelections()
                        adapterCourses.removeCourses(courseIdsToRemove)
                    }
                }
                .setNegativeButton(R.string.no, null).show()
        }

        btnArchive.setOnClickListener {
            val alertDialogBuilder = AlertDialog.Builder(ContextThemeWrapper(this.context, R.style.CustomAlertDialog))
            val message = if (countSelected() == 1) {
                R.string.are_you_sure_you_want_to_archive_this_course
            } else {
                R.string.are_you_sure_you_want_to_archive_these_courses
            }
            alertDialogBuilder.setMessage(message)
                .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                    val courseIdsToRemove = selectedItems?.mapNotNull { it?.courseId } ?: emptyList()
                    viewLifecycleOwner.lifecycleScope.launch {
                        deleteSelected(true)
                        clearAllSelections()
                        adapterCourses.removeCourses(courseIdsToRemove)
                    }
                }
                .setNegativeButton(R.string.no, null).show()
        }

        requireView().findViewById<View>(R.id.btn_collections).setOnClickListener {
            val f = CollectionsFragment.getInstance(searchTags, "courses")
            f.setListener(this)
            f.show(childFragmentManager, "")
        }
    }

    private fun setupMyProgressButton() {
        if (isMyCourseLib) {
            requireView().findViewById<View>(R.id.fabMyProgress).apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    val progressFragment = CoursesProgressFragment().apply {
                        arguments = Bundle().apply {
                            putBoolean("isMyCourseLib", true)
                        }
                    }

                    FragmentNavigator.replaceFragment(
                        parentFragmentManager,
                        R.id.fragment_container,
                        progressFragment,
                        addToBackStack = true
                    )
                }
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnHomeItemClickListener) {
            homeItemClickListener = context
        }
    }

    private fun additionalSetup() {
        val bottomSheet = requireView().findViewById<View>(R.id.card_filter)
        requireView().findViewById<View>(R.id.filter).setOnClickListener {
            bottomSheet.visibility = if (bottomSheet.isVisible) View.GONE else View.VISIBLE
        }
        orderByDate = requireView().findViewById(R.id.order_by_date_button)
        orderByTitle = requireView().findViewById(R.id.order_by_title_button)
        // Disabled until adapterCourses is ready; enabled in getAdapter()/loadDataAsync().
        orderByDate.isEnabled = false
        orderByTitle.isEnabled = false
        orderByDate.setOnClickListener {
            adapterCourses.toggleSortOrder { scrollToTop() }
        }
        orderByTitle.setOnClickListener {
            adapterCourses.toggleTitleSortOrder { scrollToTop() }
        }
    }

    private fun enableSortButtons() {
        if (::orderByDate.isInitialized) orderByDate.isEnabled = true
        if (::orderByTitle.isInitialized) orderByTitle.isEnabled = true
    }

    private fun initializeView() {
        tvAddToLib = requireView().findViewById(R.id.tv_add)
        tvAddToLib.setOnClickListener {
            if ((selectedItems?.size ?: 0) > 0) {
                confirmation = createAlertDialog()
                confirmation.show()
            }
        }
        etSearch = requireView().findViewById(R.id.et_search)
        tvSelected = requireView().findViewById(R.id.tv_selected)
        btnRemove = requireView().findViewById(R.id.btn_remove)
        btnArchive = requireView().findViewById(R.id.btn_archive)
        spnGrade = requireView().findViewById(R.id.spn_grade)
        spnSubject = requireView().findViewById(R.id.spn_subject)
        tvMessage = requireView().findViewById(R.id.tv_message)
        requireView().findViewById<View>(R.id.tl_tags).visibility = View.GONE
        tvFragmentInfo = requireView().findViewById(R.id.tv_fragment_info)

        setupSpinners()
        setupSelectAll()
        checkList()
    }

    private fun setupSpinners() {
        val gradeAdapter = ArrayAdapter.createFromResource(requireContext(), R.array.grade_level, R.layout.spinner_item)
        gradeAdapter.setDropDownViewResource(R.layout.custom_simple_list_item_1)
        spnGrade.adapter = gradeAdapter

        val subjectAdapter = ArrayAdapter.createFromResource(requireContext(), R.array.subject_level, R.layout.spinner_item)
        subjectAdapter.setDropDownViewResource(R.layout.custom_simple_list_item_1)
        spnSubject.adapter = subjectAdapter

        spnGrade.onItemSelectedListener = itemSelectedListener
        spnSubject.onItemSelectedListener = itemSelectedListener
    }

    private fun setupSelectAll() {
        selectAll = requireView().findViewById(R.id.selectAllCourse)
        if (userModel?.isGuest() == true) {
            tvAddToLib.visibility = View.GONE
            btnRemove.visibility = View.GONE
            btnArchive.visibility = View.GONE
            selectAll.visibility = View.GONE
        }

        selectAll.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSelectAllState) return@setOnCheckedChangeListener
            if (!::adapterCourses.isInitialized) return@setOnCheckedChangeListener
            hideButtons()
            adapterCourses.selectAllItems(isChecked)
            selectAll.text = if (isChecked) getString(R.string.unselect_all) else getString(R.string.select_all)
        }
    }

    private fun hideButtons() {
        val count = selectedItems.orEmpty().size
        btnArchive.isEnabled = count != 0
        btnRemove.isEnabled = count != 0
        if (count != 0) {
            if (isMyCourseLib) {
                btnArchive.visibility = View.VISIBLE
                btnRemove.visibility = View.VISIBLE
            } else {
                tvAddToLib.visibility = View.VISIBLE
            }
        } else {
            if (isMyCourseLib) {
                btnArchive.visibility = View.GONE
                btnRemove.visibility = View.GONE
            } else {
                tvAddToLib.visibility = View.GONE
            }
        }
    }

    private fun checkList() {
        if (!::adapterCourses.isInitialized) return
        if (adapterCourses.currentList.isEmpty()) {
            selectAll.visibility = View.GONE
            etSearch.visibility = View.GONE
            tvAddToLib.visibility = View.GONE
            requireView().findViewById<View>(R.id.filter).visibility = View.GONE
            btnRemove.visibility = View.GONE
            tvSelected.visibility = View.GONE
            btnArchive.visibility = View.GONE
        } else {
            etSearch.visibility = View.VISIBLE
            requireView().findViewById<View>(R.id.filter).visibility = View.VISIBLE
            if (userModel?.isGuest() == false) {
                val showSelectAll = isMyCourseLib || adapterCourses.currentList.any { !it.isMyCourse }
                selectAll.visibility = if (showSelectAll) View.VISIBLE else View.GONE
            }
        }
    }

    private val itemSelectedListener: AdapterView.OnItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, i: Int, l: Long) {
            if (view == null) {
                return
            }
            gradeLevel = if (spnGrade.selectedItem.toString() == "All") "" else spnGrade.selectedItem.toString()
            subjectLevel = if (spnSubject.selectedItem.toString() == "All") "" else spnSubject.selectedItem.toString()
            filterCoursesAndUpdateUi()
            if (!::adapterCourses.isInitialized) return
            showNoFilter(tvMessage, adapterCourses.itemCount)
            scrollToTop()
        }

        override fun onNothingSelected(adapterView: AdapterView<*>?) {}
    }

    private fun clearTags() {
        requireView().findViewById<View>(R.id.btn_clear_tags).setOnClickListener {
            searchTags.clear()
            etSearch.setText(R.string.empty_text)
            tvSelected.text = context?.getString(R.string.empty_text)
            spnGrade.setSelection(0)
            spnSubject.setSelection(0)
            filterCoursesAndUpdateUi()
            scrollToTop()
        }
    }

    private fun filterCoursesAndUpdateUi() {
        if (!::adapterCourses.isInitialized) return
        val searchText = etSearch.text.toString().trim()
        val selectedGrade = if (spnGrade.selectedItem.toString() == "All") "" else spnGrade.selectedItem.toString()
        val selectedSubject = if (spnSubject.selectedItem.toString() == "All") "" else spnSubject.selectedItem.toString()
        val tagNames = searchTags.mapNotNull { it.name }

        val userId = model?.id
        viewModel.filterCourses(isMyCourseLib, userId, searchText, selectedGrade, selectedSubject, tagNames)
        scrollToTop()
    }

    private fun createAlertDialog(): AlertDialog {
        val builder = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
        val msg = buildString {
            append(getString(R.string.success_you_have_added_the_following_courses))
            val itemsSize = selectedItems?.size ?: 0
            if (itemsSize <= 5) {
                selectedItems?.forEach { item ->
                    append(" - ").append(item?.courseTitle).append(" \n")
                }
            } else {
                for (i in 0..4) {
                    append(" - ").append(selectedItems?.get(i)?.courseTitle).append(" \n")
                }
                append(getString(R.string.and)).append(itemsSize - 5)
                    .append(getString(R.string.more_course_s))
            }
            append(getString(R.string.return_to_the_home_tab_to_access_mycourses))
        }
        builder.setMessage(msg)
        builder.setCancelable(true)
            .setPositiveButton(R.string.go_to_mycourses) { _: DialogInterface, _: Int ->
                if (userModel?.id?.startsWith("guest") == true) {
                    DialogUtils.guestDialog(requireContext(), profileDbHandler)
                } else {
                    val fragment = CoursesFragment().apply {
                        arguments = Bundle().apply {
                            putBoolean("isMyCourseLib", true)
                        }
                    }
                    homeItemClickListener?.openMyFragment(fragment)
                }
            }
            .setNegativeButton(R.string.ok) { dialog: DialogInterface, _: Int ->
                dialog.cancel()
            }
            .setOnDismissListener {
                addToMyList()
            }

        return builder.create()
    }

    override fun onSelectedListChange(list: MutableList<Course?>) {
        selectionJob?.cancel()
        selectionJob = viewLifecycleOwner.lifecycleScope.launch {
            val realmCourses = list.mapNotNull { course ->
                course?.let {
                    var rc = coursesRepository.getCourseById(it.courseId)
                    if (rc == null) {
                        // Create unmanaged
                        rc = RealmMyCourse()
                        rc.courseId = it.courseId
                        rc.courseTitle = it.courseTitle
                        rc.isMyCourse = it.isMyCourse
                    }
                    rc
                }
            }.toMutableList<RealmMyCourse?>()

            withContext(dispatcherProvider.main) {
                selectedItems = realmCourses
                changeButtonStatus()
                hideButtons()
            }
        }
    }

    override fun onTagClicked(tag: Tag) {
        val realmTag = RealmTag()
        realmTag.name = tag.name
        realmTag.id = tag.id
        onTagClicked(realmTag)
    }

    // Existing onTagClicked(tag: RealmTag) handles logic.

    override fun onTagClicked(tag: RealmTag) {
        if (!searchTags.any { it.name == tag.name }) {
            searchTags.add(tag)
        }
        filterCoursesAndUpdateUi()
        showTagText(searchTags, tvSelected)
        scrollToTop()
    }

    private fun updateCheckBoxState(programmaticState: Boolean) {
        isUpdatingSelectAllState = true
        selectAll.isChecked = programmaticState
        isUpdatingSelectAllState = false
    }

    private fun clearAllSelections() {
        if (::adapterCourses.isInitialized) {
            adapterCourses.selectAllItems(false)
            updateCheckBoxState(false)
            selectAll.text = getString(R.string.select_all)
        }
    }

    private fun changeButtonStatus() {
        tvAddToLib.isEnabled = (selectedItems?.size ?: 0) > 0
        btnRemove.isEnabled = (selectedItems?.size ?: 0) > 0
        btnArchive.isEnabled = (selectedItems?.size ?: 0) > 0

        if (::adapterCourses.isInitialized) {
            val allSelected = adapterCourses.areAllSelected()
            updateCheckBoxState(allSelected)
            selectAll.text = if (allSelected) getString(R.string.unselect_all) else getString(R.string.select_all)
        }
    }

    override fun onTagSelected(tag: RealmTag) {
        val li: MutableList<RealmTag> = ArrayList()
        li.add(tag)
        searchTags = li
        tvSelected.text = context?.getString(R.string.tag_selected, tag.name)
        filterCoursesAndUpdateUi()
        scrollToTop()
        showNoData(tvMessage, adapterCourses.itemCount, "courses")
    }

    override fun onOkClicked(list: List<RealmTag>?) {
        searchTags.clear()
        list?.forEach { tag ->
            if (!searchTags.any { it.name == tag.name }) {
                searchTags.add(tag)
            }
        }
        filterCoursesAndUpdateUi()
        scrollToTop()
    }

    private fun filterApplied(): Boolean {
        return !(searchTags.isEmpty() && gradeLevel.isEmpty() && subjectLevel.isEmpty() && etSearch.text.toString().isEmpty())
    }

    private fun saveSearchActivity() {
        if (filterApplied()) {
            val searchText = etSearch.text.toString()
            val userName = "${model?.name}"
            val planetCode = "${model?.planetCode}"
            val parentCode = "${model?.parentCode}"
            val tags = searchTags.toList()
            val grade = gradeLevel
            val subject = subjectLevel
            lifecycleScope.launch {
                coursesRepository.saveSearchActivity(
                    searchText,
                    userName,
                    planetCode,
                    parentCode,
                    tags,
                    grade,
                    subject
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        saveSearchActivity()
        clearAllSelections()
    }

    override fun onDestroy() {
        customProgressDialog?.dismiss()
        customProgressDialog = null
        super.onDestroy()
    }

    override fun getWatchedTables(): List<String> {
        return listOf("courses")
    }

    override fun onDataUpdated(table: String, update: TableDataUpdate) {
        if (table == "courses" && update.shouldRefreshUI) {
            loadDataAsync()
        }
    }

    override fun shouldAutoRefresh(table: String): Boolean = false

    override fun getSyncRecyclerView(): RecyclerView? {
        return if (::recyclerView.isInitialized) recyclerView else null
    }

    override fun onDestroyView() {
        searchTextWatcher?.let { etSearch.removeTextChangedListener(it) }
        searchTextWatcher = null
        super.onDestroyView()
    }

    override fun onRatingChanged() {
        if (!::adapterCourses.isInitialized) {
            super.onRatingChanged()
            return
        }
        filterCoursesAndUpdateUi()
    }

    private fun RealmMyCourse.toCourse(): Course {
        return Course(
            courseId = this.courseId ?: "",
            courseTitle = this.courseTitle ?: "",
            description = this.description ?: "",
            gradeLevel = this.gradeLevel ?: "",
            subjectLevel = this.subjectLevel ?: "",
            createdDate = this.createdDate,
            numberOfSteps = this.getNumberOfSteps(),
            isMyCourse = this.isMyCourse
        )
    }

}
