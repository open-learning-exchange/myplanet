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
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.callback.OnCourseItemSelected
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.TableDataUpdate
import org.ole.planet.myplanet.callback.TagClickListener
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmSearchActivity
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmTag.Companion.getTagsArray
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.ui.resources.CollectionsFragment
import org.ole.planet.myplanet.ui.sync.RealtimeSyncHelper
import org.ole.planet.myplanet.ui.sync.RealtimeSyncMixin
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.KeyboardUtils.setupUI

@AndroidEntryPoint
class CoursesFragment : BaseRecyclerFragment<RealmMyCourse?>(), OnCourseItemSelected, TagClickListener, RealtimeSyncMixin {

    companion object {
        fun newInstance(isMyCourseLib: Boolean): CoursesFragment {
            val fragment = CoursesFragment()
            val args = Bundle()
            args.putBoolean("isMyCourseLib", isMyCourseLib)
            fragment.arguments = args
            return fragment
        }
    }

    private val viewModel: CoursesViewModel by viewModels()

    private lateinit var tvAddToLib: TextView
    private lateinit var tvSelected: TextView
    private lateinit var etSearch: EditText
    private lateinit var adapterCourses: AdapterCourses
    private lateinit var btnRemove: Button
    private lateinit var btnArchive: Button
    private lateinit var orderByDate: Button
    private lateinit var orderByTitle: Button
    private lateinit var selectAll: CheckBox
    var userModel: RealmUserModel ?= null
    lateinit var spnGrade: Spinner
    lateinit var spnSubject: Spinner
    lateinit var searchTags: MutableList<RealmTag>
    private lateinit var confirmation: AlertDialog
    private var isUpdatingSelectAllState = false
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null
    private var originalCourseList: List<RealmMyCourse?> = emptyList()
    private var ratingData: Map<String?, JsonObject> = emptyMap()
    private var progressData: Map<String?, JsonObject> = emptyMap()

    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler

    private lateinit var realtimeSyncHelper: RealtimeSyncHelper

    override fun getLayout(): Int {
        return R.layout.fragment_my_course
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.coursesState.collect { state ->
                        handleCoursesState(state)
                    }
                }
                launch {
                    viewModel.syncState.collect { state ->
                        handleSyncState(state)
                    }
                }
            }
        }
    }

    private fun handleCoursesState(state: CoursesViewModel.CoursesUiState) {
        when (state) {
            is CoursesViewModel.CoursesUiState.Loading -> {
                if (::adapterCourses.isInitialized) {
                    adapterCourses.setCourseList(emptyList())
                }
            }
            is CoursesViewModel.CoursesUiState.Success -> {
                originalCourseList = state.courses.map { it }
                ratingData = state.ratings
                progressData = state.progress
                if (::adapterCourses.isInitialized) {
                    adapterCourses.setOriginalCourseList(originalCourseList)
                    adapterCourses.updateRatingMap(ratingData)
                    adapterCourses.setProgressMap(progressData)
                    updateCourseDisplay()
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    if (isMyCourseLib) {
                        val courseIds = originalCourseList.mapNotNull { it.id }
                        resources = viewModel.getCourseLibraryItems(courseIds)
                        courseLib = "courses"
                    }
                }
            }
            is CoursesViewModel.CoursesUiState.Error -> {
                if (isAdded) {
                    Snackbar.make(requireView(), state.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handleSyncState(state: CoursesViewModel.SyncState) {
        when (state) {
            is CoursesViewModel.SyncState.Syncing -> {
                if (customProgressDialog == null) {
                    customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
                    customProgressDialog?.setText(getString(R.string.syncing_courses_data))
                }
                customProgressDialog?.show()
            }
            is CoursesViewModel.SyncState.Success -> {
                customProgressDialog?.setText(getString(R.string.loading_courses))
            }
            is CoursesViewModel.SyncState.Error -> {
                customProgressDialog?.dismiss()
                customProgressDialog = null
                if (isAdded) {
                    Snackbar.make(requireView(), state.message, Snackbar.LENGTH_LONG).setAction(R.string.retry) {
                        viewModel.startCoursesSyncIfNeeded()
                    }.show()
                }
            }
            is CoursesViewModel.SyncState.Idle -> {
                customProgressDialog?.dismiss()
                customProgressDialog = null
            }
        }
    }

    private fun updateCourseDisplay() {
        if (!::adapterCourses.isInitialized) return
        val filteredCourses = filterCourses(etSearch.text.toString().trim(), searchTags)
        adapterCourses.setCourseList(filteredCourses)
        scrollToTop()
        showNoData(tvMessage, adapterCourses.itemCount, "courses")
        checkList()
    }

    private fun filterCourses(query: String, tags: List<RealmTag>): List<RealmMyCourse?> {
        var courses = getFullCourseList()
        if (query.isNotEmpty()) {
            courses = filterByQuery(courses, query)
        }
        if (tags.isNotEmpty()) {
            courses = filterCoursesByTags(courses, tags)
        }
        return applyGradeAndSubjectFilter(courses)
    }

    private fun filterByQuery(courses: List<RealmMyCourse?>, query: String): List<RealmMyCourse?> {
        val normalizedQuery = normalizeText(query)
        val queryParts = query.split(" ").filter { it.isNotBlank() }
        val startsWithMatches = mutableListOf<RealmMyCourse?>()
        val containsMatches = mutableListOf<RealmMyCourse?>()
        courses.forEach { course ->
            val title = course?.courseTitle ?: return@forEach
            val normalizedTitle = normalizeText(title)
            if (normalizedTitle.startsWith(normalizedQuery, ignoreCase = true)) {
                startsWithMatches.add(course)
            } else if (queryParts.all { normalizedTitle.contains(normalizeText(it), ignoreCase = true) }) {
                containsMatches.add(course)
            }
        }
        return startsWithMatches + containsMatches
    }

    private fun filterCoursesByTags(courses: List<RealmMyCourse?>, tags: List<RealmTag>): List<RealmMyCourse?> {
        if (!isRealmInitialized()) return courses
        val filtered = mutableListOf<RealmMyCourse?>()
        courses.forEach { course ->
            tags.forEach { tag ->
                val hasTag = mRealm.where(RealmTag::class.java)
                    .equalTo("db", "courses")
                    .equalTo("tagId", tag.id)
                    .equalTo("linkId", course?.courseId)
                    .count() > 0
                if (hasTag && !filtered.contains(course)) {
                    filtered.add(course)
                }
            }
        }
        return filtered
    }

    private fun applyGradeAndSubjectFilter(courses: List<RealmMyCourse?>): List<RealmMyCourse?> {
        if (gradeLevel.isEmpty() && subjectLevel.isEmpty()) return courses
        return courses.filter { course ->
            val matchesGrade = gradeLevel.isNotEmpty() && course?.gradeLevel == gradeLevel
            val matchesSubject = subjectLevel.isNotEmpty() && course?.subjectLevel == subjectLevel
            matchesGrade || matchesSubject
        }
    }

    private fun scrollToTop() {
        recyclerView.post {
            if ((recyclerView.adapter?.itemCount ?: 0) > 0) {
                recyclerView.scrollToPosition(0)
            }
        }
    }

    private fun getFullCourseList(): List<RealmMyCourse?> {
        return originalCourseList.filter { !it?.courseTitle.isNullOrBlank() }
            .sortedWith(compareBy({ it?.isMyCourse }, { it?.courseTitle }))
    }

    override fun getAdapter(): RecyclerView.Adapter<*> {
        adapterCourses = AdapterCourses(
            requireActivity(),
            originalCourseList,
            HashMap(ratingData),
            userProfileDbHandler,
        )
        adapterCourses.setProgressMap(progressData)
        adapterCourses.setmRealm(mRealm)
        adapterCourses.setListener(this)
        adapterCourses.setRatingChangeListener(this)
        return adapterCourses
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userModel = UserProfileDbHandler(requireContext()).userModel
        searchTags = ArrayList()
        initializeView()
        updateCheckBoxState(false)
        setupButtonVisibility()
        setupEventListeners()
        clearTags()
        showNoData(tvMessage, adapterCourses.itemCount, "courses")
        setupUI(requireView().findViewById(R.id.my_course_parent_layout), requireActivity())

        if (!isMyCourseLib) tvFragmentInfo.setText(R.string.our_courses)
        additionalSetup()
        setupMyProgressButton()
        observeViewModel()
        viewModel.loadCourses(userModel?.id, isMyCourseLib)
        viewModel.startCoursesSyncIfNeeded()

        realtimeSyncHelper = RealtimeSyncHelper(this, this)
        realtimeSyncHelper.setupRealtimeSync()
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
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (!etSearch.isFocused) return
                updateCourseDisplay()
            }
            override fun afterTextChanged(s: Editable) {}
        })

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
                    val newFragment = CoursesFragment()
                    recreateFragment(newFragment)
                    checkList()
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
                    val newFragment = CoursesFragment()
                    recreateFragment(newFragment)
                    checkList()
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
                    val myProgressFragment = MyProgressFragment().apply {
                        arguments = Bundle().apply {
                            putBoolean("isMyCourseLib", true)
                        }
                    }

                    NavigationHelper.replaceFragment(
                        parentFragmentManager,
                        R.id.fragment_container,
                        myProgressFragment,
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
        orderByDate.setOnClickListener { adapterCourses.toggleSortOrder() }
        orderByTitle.setOnClickListener { adapterCourses.toggleTitleSortOrder() }
    }

    private fun initializeView() {
        tvAddToLib = requireView().findViewById(R.id.tv_add)
        tvAddToLib.setOnClickListener {
            if ((selectedItems?.size ?: 0) > 0) {
                confirmation = createAlertDialog()
                confirmation.show()
                addToMyList()
                selectedItems?.clear()
                tvAddToLib.isEnabled = false
                checkList()
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
        if (adapterCourses.getCourseList().isEmpty()) {
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
            val allMyCourses = adapterCourses.getCourseList().all { it?.isMyCourse == true }
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
            updateCourseDisplay()
            showNoFilter(tvMessage, adapterCourses.itemCount)
        }

        override fun onNothingSelected(adapterView: AdapterView<*>?) {}
    }

    private fun clearTags() {
        requireView().findViewById<View>(R.id.btn_clear_tags).setOnClickListener {
            searchTags.clear()
            etSearch.setText(R.string.empty_text)
            tvSelected.text = context?.getString(R.string.empty_text)
            updateCourseDisplay()
            spnGrade.setSelection(0)
            spnSubject.setSelection(0)
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
                    DialogUtils.guestDialog(requireContext())
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
                val newFragment = CoursesFragment()
                recreateFragment(newFragment)
            }
            .setOnDismissListener {
                val newFragment = CoursesFragment()
                recreateFragment(newFragment)
            }

        return builder.create()
    }

    override fun onSelectedListChange(list: MutableList<RealmMyCourse?>) {
        selectedItems = list
        changeButtonStatus()
        hideButtons()
    }

    override fun onTagClicked(tag: RealmTag?) {
        if (!searchTags.contains(tag)) {
            tag?.let { searchTags.add(it) }
        }
        updateCourseDisplay()
        showTagText(searchTags, tvSelected)
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

        val allSelected = adapterCourses.areAllSelected()
        updateCheckBoxState(allSelected)
        selectAll.text = if (allSelected) getString(R.string.unselect_all) else getString(R.string.select_all)
    }

    override fun onTagSelected(tag: RealmTag) {
        val li: MutableList<RealmTag> = ArrayList()
        li.add(tag)
        searchTags = li
        tvSelected.text = context?.getString(R.string.tag_selected, tag.name)
        updateCourseDisplay()
    }

    override fun onOkClicked(list: List<RealmTag>?) {
        if (list?.isEmpty() == true) {
            searchTags.clear()
            updateCourseDisplay()
        } else {
            for (tag in list ?: emptyList()) {
                onTagClicked(tag)
            }
        }
    }

    private fun filterApplied(): Boolean {
        return !(searchTags.isEmpty() && gradeLevel.isEmpty() && subjectLevel.isEmpty() && etSearch.text.toString().isEmpty())
    }

    private fun saveSearchActivity() {
        if (filterApplied()) {
            if (!mRealm.isInTransaction) mRealm.beginTransaction()
            val activity = mRealm.createObject(RealmSearchActivity::class.java, UUID.randomUUID().toString())
            activity.user = "${model?.name}"
            activity.time = Calendar.getInstance().timeInMillis
            activity.createdOn = "${model?.planetCode}"
            activity.parentCode = "${model?.parentCode}"
            activity.text = etSearch.text.toString()
            activity.type = "courses"
            val filter = JsonObject()

            filter.add("tags", getTagsArray(searchTags.toList()))
            filter.addProperty("doc.gradeLevel", gradeLevel)
            filter.addProperty("doc.subjectLevel", subjectLevel)
            activity.filter = Gson().toJson(filter)
            mRealm.commitTransaction()
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
                NavigationHelper.replaceFragment(
                    parentFragmentManager,
                    R.id.fragment_container,
                    fragment,
                    addToBackStack = true,
                    allowStateLoss = true
                )
            } else {
                NavigationHelper.replaceFragment(
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
            viewModel.loadCourses(userModel?.id, isMyCourseLib)
        }
    }

    override fun getSyncRecyclerView(): RecyclerView? {
        return if (::recyclerView.isInitialized) recyclerView else null
    }

    override fun onDestroyView() {
        if (::realtimeSyncHelper.isInitialized) {
            realtimeSyncHelper.cleanup()
        }
        super.onDestroyView()
    }
}
