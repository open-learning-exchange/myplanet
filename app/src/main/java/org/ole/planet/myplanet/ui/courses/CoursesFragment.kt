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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.callback.OnCourseItemSelectedListener
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.callback.OnTagClickListener
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.services.sync.SyncManager
import org.ole.planet.myplanet.ui.components.FragmentNavigator
import org.ole.planet.myplanet.ui.resources.CollectionsFragment
import org.ole.planet.myplanet.ui.sync.RealtimeSyncHelper
import org.ole.planet.myplanet.ui.sync.RealtimeSyncMixin
import org.ole.planet.myplanet.utils.DialogUtils
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

    @Inject
    lateinit var prefManager: SharedPrefManager

    @Inject
    lateinit var serverUrlMapper: ServerUrlMapper

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var userSessionManager: UserSessionManager

    @Inject
    lateinit var progressRepository: ProgressRepository

    @Inject
    lateinit var ratingsRepository: RatingsRepository
    private val serverUrl: String
        get() = settings.getString("serverURL", "") ?: ""

    private lateinit var realtimeSyncHelper: RealtimeSyncHelper

    override fun getLayout(): Int {
        return R.layout.fragment_my_course
    }

    private fun startCoursesSync() {
        val isFastSync = settings.getBoolean("fastSync", false)
        if (isFastSync && !prefManager.isCoursesSynced()) {
            checkServerAndStartSync()
        }
    }

    private fun checkServerAndStartSync() {
        val mapping = serverUrlMapper.processUrl(serverUrl)

        lifecycleScope.launch {
            updateServerIfNecessary(mapping)
            startSyncManager()
        }
    }

    private fun startSyncManager() {
        syncManager.start(object : OnSyncListener {
            override fun onSyncStarted() {
                viewLifecycleOwner.lifecycleScope.launch {
                    if (isAdded && !requireActivity().isFinishing) {
                        customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
                        customProgressDialog?.setText(getString(R.string.syncing_courses_data))
                        customProgressDialog?.show()
                    }
                }
            }

            override fun onSyncComplete() {
                viewLifecycleOwner.lifecycleScope.launch {
                    if (isAdded) {
                        customProgressDialog?.setText(getString(R.string.loading_courses))

                        lifecycleScope.launch {
                            delay(3000)
                            withContext(Dispatchers.Main) {
                                customProgressDialog?.dismiss()
                                customProgressDialog = null
                                loadDataAsync()
                            }
                        }
                        prefManager.setCoursesSynced(true)
                    }
                }
            }

            override fun onSyncFailed(msg: String?) {
                viewLifecycleOwner.lifecycleScope.launch {
                    if (isAdded) {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null

                        Snackbar.make(requireView(), "Sync failed: ${msg ?: "Unknown error"}", Snackbar.LENGTH_LONG).setAction("Retry") {
                            startCoursesSync()
                        }.show()
                    }
                }
            }
        }, "full", listOf("courses"))
    }

    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping, settings) { url ->
            isServerReachable(url)
        }
    }

    private fun scrollToTop() {
        recyclerView.post {
            if ((recyclerView.adapter?.itemCount ?: 0) > 0) {
                recyclerView.scrollToPosition(0)
            }
        }
    }


    private fun loadDataAsync() {
        if (!isAdded || requireActivity().isFinishing) return

        lifecycleScope.launch {
            try {
                val map = ratingsRepository.getCourseRatings(model?.id)
                val progressMap = progressRepository.getCourseProgress(model?.id)

                val allCourses = coursesRepository.filterCourses("", "", "", emptyList())
                allCourses.forEach { it.isMyCourse = it.userId?.contains(model?.id) == true }

                val finalCourses = if (isMyCourseLib) {
                    allCourses.filter { it.isMyCourse }
                } else {
                    allCourses
                }

                val sortedCourseList = finalCourses.sortedWith(compareBy({ it.isMyCourse }, { it.courseTitle }))

                if (isMyCourseLib) {
                    val courseIds = sortedCourseList.mapNotNull { it.id }
                    resources = coursesRepository.getCourseOfflineResources(courseIds)
                    courseLib = "courses"
                }

                recyclerView.adapter = null
                adapterCourses = CoursesAdapter(
                    requireActivity(),
                    map,
                    userModel,
                    tagsRepository
                )
                adapterCourses.submitList(sortedCourseList)
                adapterCourses.setProgressMap(progressMap)
                adapterCourses.setListener(this@CoursesFragment)
                adapterCourses.setRatingChangeListener(this@CoursesFragment)
                recyclerView.adapter = adapterCourses

                checkList()
                showNoData(tvMessage, adapterCourses.itemCount, "courses")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override suspend fun getAdapter(): RecyclerView.Adapter<*> {
        val allCourses = coursesRepository.filterCourses("", "", "", emptyList())
        allCourses.forEach { it.isMyCourse = it.userId?.contains(model?.id) == true }

        val courseList = if (isMyCourseLib) {
            allCourses.filter { it.isMyCourse }
        } else {
            allCourses.sortedWith(compareBy({ it.isMyCourse }, { it.courseTitle }))
        }

        val map = HashMap<String?, com.google.gson.JsonObject>()
        val progressMap = HashMap<String?, com.google.gson.JsonObject>()

        adapterCourses = CoursesAdapter(requireActivity(), map, userModel, tagsRepository)
        adapterCourses.submitList(courseList) {
            if (isAdded && view != null && ::selectAll.isInitialized) {
                selectedItems?.clear()
                clearAllSelections()
                checkList()
            }
        }
        adapterCourses.setProgressMap(progressMap)
        adapterCourses.setListener(this@CoursesFragment)
        adapterCourses.setRatingChangeListener(this@CoursesFragment)
        return adapterCourses
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(requireView().findViewById(R.id.my_course_parent_layout), requireActivity())
        additionalSetup()
        setupMyProgressButton()
        viewLifecycleOwner.lifecycleScope.launch {
            userModel = userSessionManager.getUserModel()
            searchTags = ArrayList()
            initializeView()
            setupButtonVisibility()
            setupEventListeners()
            clearTags()
            if (!isMyCourseLib) tvFragmentInfo.setText(R.string.our_courses)
            if (::adapterCourses.isInitialized) {
                showNoData(tvMessage, adapterCourses.itemCount, "courses")
            }
            loadDataAsync()
            updateCheckBoxState(false)
        }

        realtimeSyncHelper = RealtimeSyncHelper(this, this)
        realtimeSyncHelper.setupRealtimeSync()
        startCoursesSync()
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
                    deleteSelected(true)
                    clearAllSelections()
                    loadDataAsync()
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
                    deleteSelected(true)
                    clearAllSelections()
                    loadDataAsync()
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
        orderByDate.setOnClickListener {
            adapterCourses.toggleSortOrder {
                scrollToTop()
            }
        }
        orderByTitle.setOnClickListener {
            adapterCourses.toggleTitleSortOrder {
                scrollToTop()
            }
        }
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
            if (isUpdatingSelectAllState) {
                return@setOnCheckedChangeListener
            }
            hideButtons()
            adapterCourses.selectAllItems(isChecked)
            selectAll.text = if (isChecked) getString(R.string.unselect_all) else getString(R.string.select_all)
        }
    }

    private fun hideButtons() {
        btnArchive.isEnabled = selectedItems?.size!! != 0
        btnRemove.isEnabled = selectedItems?.size!! != 0
        if (selectedItems?.size!! != 0) {
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
            val allMyCourses = adapterCourses.currentList.all { it.isMyCourse }
            if (userModel?.isGuest() == false) {
                selectAll.visibility = if (allMyCourses) View.GONE else View.VISIBLE
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
        val searchText = etSearch.text.toString().trim()
        val selectedGrade = if (spnGrade.selectedItem.toString() == "All") "" else spnGrade.selectedItem.toString()
        val selectedSubject = if (spnSubject.selectedItem.toString() == "All") "" else spnSubject.selectedItem.toString()
        val tagNames = searchTags.mapNotNull { it.name }

        lifecycleScope.launch {
            val userId = model?.id
            val (filteredCourses, map, progressMap) = withContext(Dispatchers.IO) {
                val courses = coursesRepository.filterCourses(searchText, selectedGrade, selectedSubject, tagNames)
                val finalCourses = if (isMyCourseLib) {
                    courses.filter { it.userId?.contains(userId) == true }
                } else {
                    courses.forEach { course ->
                        course.isMyCourse = course.userId?.contains(userId) == true
                    }
                    courses.sortedWith(compareBy({ it.isMyCourse }, { it.courseTitle }))
                }
                val ratings = ratingsRepository.getCourseRatings(userId)
                val progress = progressRepository.getCourseProgress(userId)
                Triple(finalCourses, ratings, progress)
            }
            adapterCourses.updateData(filteredCourses, map, progressMap)
            scrollToTop()
            showNoData(tvMessage, filteredCourses.size, "courses")
        }
    }

    private fun createAlertDialog(): AlertDialog {
        val builder = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
        var msg = getString(R.string.success_you_have_added_the_following_courses)
        if ((selectedItems?.size ?: 0) <= 5) {
            for (i in selectedItems?.indices!!) {
                msg += " - ${selectedItems?.get(i)?.courseTitle} \n"
            }
        } else {
            for (i in 0..4) {
                msg += " - ${selectedItems?.get(i)?.courseTitle} \n"
            }
            msg += "${getString(R.string.and)}${((selectedItems?.size ?: 0) - 5)}${getString(R.string.more_course_s)}"
        }
        msg += getString(R.string.return_to_the_home_tab_to_access_mycourses)
        builder.setMessage(msg)
        builder.setCancelable(true)
            .setPositiveButton(R.string.go_to_mycourses) { dialog: DialogInterface, _: Int ->
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

    override fun onSelectedListChange(list: MutableList<RealmMyCourse?>) {
        selectedItems = list
        changeButtonStatus()
        hideButtons()
    }

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

    private fun recreateFragment(fragment: Fragment) {
        if (isAdded && activity != null && !requireActivity().isFinishing) {
            if (isMyCourseLib) {
                val args = Bundle()
                args.putBoolean("isMyCourseLib", true)
                args.putString("courseLib", courseLib)
                args.putSerializable("resources", resources?.let { ArrayList(it) })
                fragment.arguments = args
                FragmentNavigator.replaceFragment(
                    parentFragmentManager,
                    R.id.fragment_container,
                    fragment,
                    addToBackStack = true,
                    allowStateLoss = true
                )
            } else {
                FragmentNavigator.replaceFragment(
                    parentFragmentManager,
                    R.id.fragment_container,
                    fragment,
                    addToBackStack = true,
                    allowStateLoss = true
                )
            }
        }
    }

    override fun getWatchedTables(): List<String> {
        return listOf("courses")
    }

    override fun onDataUpdated(table: String, update: TableDataUpdate) {
        if (table == "courses" && update.shouldRefreshUI) {
            if (::adapterCourses.isInitialized) {
                lifecycleScope.launch {
                    val map = ratingsRepository.getCourseRatings(model?.id)
                    val progressMap = progressRepository.getCourseProgress(model?.id)

                    val allCourses = coursesRepository.filterCourses("", "", "", emptyList())
                    allCourses.forEach { it.isMyCourse = it.userId?.contains(model?.id) == true }

                    val sortedCourseList = if (isMyCourseLib) {
                        allCourses.filter { it.isMyCourse }
                    } else {
                        allCourses.sortedWith(compareBy({ it.isMyCourse }, { it.courseTitle }))
                    }

                    adapterCourses.updateData(sortedCourseList, map, progressMap)
                }
            } else {
                lifecycleScope.launch {
                    recyclerView.adapter = getAdapter()
                }
            }
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
}
