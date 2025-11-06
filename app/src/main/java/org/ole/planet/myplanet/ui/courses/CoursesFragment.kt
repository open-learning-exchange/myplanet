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
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.callback.OnCourseItemSelected
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.callback.TableDataUpdate
import org.ole.planet.myplanet.callback.TagClickListener
import org.ole.planet.myplanet.model.RealmCourseProgress.Companion.getCourseProgress
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmRating.Companion.getRatings
import org.ole.planet.myplanet.model.RealmSearchActivity
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmTag.Companion.getTagsArray
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.TagRepository
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.ui.resources.CollectionsFragment
import org.ole.planet.myplanet.ui.sync.RealtimeSyncHelper
import org.ole.planet.myplanet.ui.sync.RealtimeSyncMixin
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.KeyboardUtils.setupUI
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.SharedPrefManager

@AndroidEntryPoint
class CoursesFragment : BaseRecyclerFragment<RealmMyCourse?>(), OnCourseItemSelected, TagClickListener, RealtimeSyncMixin {
    private var _courseList: List<RealmMyCourse?> = emptyList()

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
    lateinit var prefManager: SharedPrefManager

    @Inject
    lateinit var serverUrlMapper: ServerUrlMapper

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler

    @Inject
    lateinit var tagRepository: TagRepository
    private val serverUrl: String
        get() = settings.getString("serverURL", "") ?: ""

    private lateinit var realtimeSyncHelper: RealtimeSyncHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefManager = SharedPrefManager(requireContext())
        startCoursesSync()
    }

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

        lifecycleScope.launch(Dispatchers.IO) {
            updateServerIfNecessary(mapping)
            withContext(Dispatchers.Main) {
                startSyncManager()
            }
        }
    }

    private fun startSyncManager() {
        syncManager.start(object : SyncListener {
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
                                refreshCoursesData()
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

    private fun getFullCourseList(): List<RealmMyCourse?> {
        return _courseList
    }

    private fun refreshCoursesData() {
        if (!isAdded || requireActivity().isFinishing) return

        try {
            if (!mRealm.isInTransaction) {
                mRealm.refresh()
            }
            val map = getRatings(mRealm, "course", model?.id)
            val progressMap = getCourseProgress(mRealm, model?.id)
            val courseList: List<RealmMyCourse?> = getList(RealmMyCourse::class.java).filterIsInstance<RealmMyCourse?>().filter { !it?.courseTitle.isNullOrBlank() }
            val sortedCourseList = courseList.sortedWith(compareBy({ it?.isMyCourse }, { it?.courseTitle }))

            recyclerView.adapter = null
            adapterCourses = AdapterCourses(
                requireActivity(),
                sortedCourseList,
                map,
                userProfileDbHandler,
                tagRepository,
                this@CoursesFragment
            )
            adapterCourses.setProgressMap(progressMap)
            adapterCourses.setListener(this)
            adapterCourses.setRatingChangeListener(this)
            recyclerView.adapter = adapterCourses

            if (isMyCourseLib) {
                val courseIds = courseList.mapNotNull { it?.id }
                resources = mRealm.where(RealmMyLibrary::class.java)
                    .`in`("courseId", courseIds.toTypedArray())
                    .equalTo("resourceOffline", false)
                    .isNotNull("resourceLocalAddress")
                    .findAll()
            }
            checkList()
            showNoData(tvMessage, adapterCourses.itemCount, "courses")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getAdapter(): RecyclerView.Adapter<*> {
        return adapterCourses
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userModel = userProfileDbHandler.userModel
        searchTags = ArrayList()
        initializeView()
        updateCheckBoxState(false)
        setupButtonVisibility()
        setupEventListeners()
        clearTags()

        adapterCourses = AdapterCourses(requireActivity(), emptyList(), HashMap(), userProfileDbHandler, tagRepository, this)
        recyclerView.adapter = adapterCourses

        viewLifecycleOwner.lifecycleScope.launch {
            val courseData = getCourseData()
            _courseList = courseData.courseList
            adapterCourses.setCourseList(courseData.courseList)
            adapterCourses.setRatings(courseData.ratings)
            adapterCourses.setProgressMap(courseData.progressMap)
            resources = courseData.resources
            showNoData(tvMessage, adapterCourses.itemCount, "courses")
            checkList()
        }

        setupUI(requireView().findViewById(R.id.my_course_parent_layout), requireActivity())
        if (!isMyCourseLib) tvFragmentInfo.setText(R.string.our_courses)
        additionalSetup()
        setupMyProgressButton()

        realtimeSyncHelper = RealtimeSyncHelper(this, this)
        realtimeSyncHelper.setupRealtimeSync()
    }

    private suspend fun getCourseData(): CourseData = withContext(Dispatchers.IO) {
        val realm = Realm.getDefaultInstance()
        try {
            val courseList = getList(RealmMyCourse::class.java)
                .filterIsInstance<RealmMyCourse?>()
                .filter { !it?.courseTitle.isNullOrBlank() }
            val sortedCourseList = courseList.sortedWith(compareBy({ it?.isMyCourse }, { it?.courseTitle }))
            _courseList = sortedCourseList

            val ratings = getRatings(realm, "course", model?.id).let { map ->
                HashMap<String?, JsonObject>().apply {
                    map.forEach { (key, value) -> this[key] = value }
                }
            }
            val progressMap = getCourseProgress(realm, model?.id).let { map ->
                HashMap<String?, JsonObject>().apply {
                    map.forEach { (key, value) -> this[key] = value }
                }
            }

            var resources: List<RealmMyLibrary> = emptyList()
            if (isMyCourseLib) {
                val courseIds = courseList.mapNotNull { it?.id }
                resources = realm.where(RealmMyLibrary::class.java)
                    .`in`("courseId", courseIds.toTypedArray())
                    .equalTo("resourceOffline", false)
                    .isNotNull("resourceLocalAddress")
                    .findAll()
                courseLib = "courses"
            }

            CourseData(sortedCourseList, ratings, progressMap, realm.copyFromRealm(resources))
        } finally {
            realm.close()
        }
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
                val query = s.toString().trim()
                if (query.isEmpty()) {
                    adapterCourses.setCourseList(getFullCourseList())
                    scrollToTop()
                    showNoData(tvMessage, adapterCourses.itemCount, "courses")
                } else {
                    adapterCourses.setCourseList(filterCourseByTag(etSearch.text.toString(), searchTags))
                    scrollToTop()
                    showNoData(tvMessage, adapterCourses.itemCount, "courses")
                }
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
            if (etSearch.text.toString().isEmpty() && searchTags.isEmpty() && gradeLevel.isEmpty() && subjectLevel.isEmpty()) {
                adapterCourses.setCourseList(getFullCourseList())
            } else {
                adapterCourses.setCourseList(filterCourseByTag(etSearch.text.toString(), searchTags))
            }
            scrollToTop()
            showNoFilter(tvMessage, adapterCourses.itemCount)
        }

        override fun onNothingSelected(adapterView: AdapterView<*>?) {}
    }

    private fun clearTags() {
        requireView().findViewById<View>(R.id.btn_clear_tags).setOnClickListener {
            searchTags.clear()
            etSearch.setText(R.string.empty_text)
            tvSelected.text = context?.getString(R.string.empty_text)
            adapterCourses.setCourseList(getFullCourseList())
            scrollToTop()
            showNoData(tvMessage, adapterCourses.itemCount, "courses")
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
        adapterCourses.setCourseList(filterCourseByTag(etSearch.text.toString(), searchTags))
        scrollToTop()
        showTagText(searchTags, tvSelected)
        showNoData(tvMessage, adapterCourses.itemCount, "courses")
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
        adapterCourses.setCourseList(filterCourseByTag(etSearch.text.toString(), li))
        scrollToTop()
        showNoData(tvMessage, adapterCourses.itemCount, "courses")
    }

    override fun onOkClicked(list: List<RealmTag>?) {
        if (list?.isEmpty() == true) {
            searchTags.clear()
            if (etSearch.text.toString().isEmpty() && gradeLevel.isEmpty() && subjectLevel.isEmpty()) {
                adapterCourses.setCourseList(getFullCourseList())
            } else {
                adapterCourses.setCourseList(filterCourseByTag(etSearch.text.toString(), searchTags))
            }
            scrollToTop()
            showNoData(tvMessage, adapterCourses.itemCount, "courses")
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
            if (::adapterCourses.isInitialized) {
                refreshCoursesData()
            } else {
                recyclerView.adapter = getAdapter()
            }
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

    override fun onRatingChanged() {
        if (!::adapterCourses.isInitialized) {
            super.onRatingChanged()
            return
        }

        if (!mRealm.isInTransaction) {
            mRealm.refresh()
        }
        val map = getRatings(mRealm, "course", model?.id)
        val progressMap = getCourseProgress(mRealm, model?.id)

        val filteredCourseList = if (etSearch.text.toString().isEmpty() && searchTags.isEmpty() && gradeLevel.isEmpty() && subjectLevel.isEmpty()) {
            getFullCourseList()
        } else {
            filterCourseByTag(etSearch.text.toString(), searchTags)
        }

        adapterCourses = AdapterCourses(
            requireActivity(), filteredCourseList, map, userProfileDbHandler,
            tagRepository, this@CoursesFragment
        )
        adapterCourses.setProgressMap(progressMap)
        adapterCourses.setListener(this)
        adapterCourses.setRatingChangeListener(this)
        recyclerView.adapter = adapterCourses
    }
}
