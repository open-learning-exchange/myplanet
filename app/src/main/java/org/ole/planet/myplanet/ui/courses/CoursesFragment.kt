package org.ole.planet.myplanet.ui.courses

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.callback.OnCourseItemSelectedListener
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnTagClickListener
import org.ole.planet.myplanet.model.Course
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.model.Tag
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.ui.components.FragmentNavigator
import org.ole.planet.myplanet.ui.resources.CollectionsFragment
import org.ole.planet.myplanet.ui.sync.RealtimeSyncHelper
import org.ole.planet.myplanet.ui.sync.RealtimeSyncMixin
import org.ole.planet.myplanet.utils.DialogUtils
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.KeyboardUtils.setupUI

@AndroidEntryPoint
class CoursesFragment : BaseRecyclerFragment<RealmMyCourse?>(), OnCourseItemSelectedListener, OnTagClickListener, RealtimeSyncMixin {
    private lateinit var adapterCourses: CoursesAdapter
    private lateinit var orderByDate: Button
    private lateinit var orderByTitle: Button
    private lateinit var filterController: CourseFilterController
    private lateinit var selectionController: CourseSelectionController
    var userModel: RealmUser? = null
    private lateinit var confirmation: AlertDialog
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null
    private var selectionJob: Job? = null
    private val viewModel: CoursesViewModel by viewModels()

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var prefManager: SharedPrefManager

    @Inject
    lateinit var userSessionManager: UserSessionManager

    private lateinit var realtimeSyncHelper: RealtimeSyncHelper

    override fun getLayout(): Int = R.layout.fragment_my_course

    private fun scrollToTop() {
        recyclerView.post {
            if ((recyclerView.adapter?.itemCount ?: 0) > 0) recyclerView.scrollToPosition(0)
        }
    }

    private fun loadDataAsync() {
        val hostActivity = activity ?: return
        if (hostActivity.isFinishing) return
        viewModel.loadCourses(isMyCourseLib, model?.id)
    }

    override suspend fun postAddRefresh() {
        loadDataAsync()
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

        val cachedState = viewModel.coursesState.value
        if (cachedState.courses.isNotEmpty()) {
            adapterCourses.setProgressMap(cachedState.progressMap)
            adapterCourses.setRatingMap(cachedState.map)
            adapterCourses.setTagsMap(cachedState.tagsMap)
            adapterCourses.submitList(cachedState.courses) {
                if (isAdded) showNoData(tvMessage, cachedState.courses.size, "courses")
            }
        }

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
            initializeView()
            setupButtonVisibility()
            setupEventListeners()
            if (!isMyCourseLib) tvFragmentInfo.setText(R.string.our_courses)
            if (::adapterCourses.isInitialized) {
                showNoData(tvMessage, adapterCourses.itemCount, "courses")
            }
            selectionController.clearAll(null)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.coursesState.collectLatest { state ->
                if (!::adapterCourses.isInitialized) return@collectLatest

                if (isMyCourseLib) {
                    val courseIds = state.courses.mapNotNull { it.courseId }
                    resources = coursesRepository.getCourseOfflineResources(courseIds)
                    courseLib = "courses"
                }

                adapterCourses.setProgressMap(state.progressMap)
                adapterCourses.setRatingMap(state.map)
                adapterCourses.setTagsMap(state.tagsMap)
                adapterCourses.submitList(state.courses) {
                    if (isAdded && ::selectionController.isInitialized) {
                        selectedItems?.clear()
                        selectionController.clearAll(adapterCourses)
                        checkList()
                        showNoData(tvMessage, state.courses.size, "courses")
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.syncStatus.collectLatest { status ->
                when (status) {
                    is SyncStatus.Idle -> {}
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
                                .setAction("Retry") { viewModel.startCoursesSync() }
                                .show()
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

    private fun initializeView() {
        tvMessage = requireView().findViewById(R.id.tv_message)
        requireView().findViewById<View>(R.id.tl_tags).visibility = View.GONE
        tvFragmentInfo = requireView().findViewById(R.id.tv_fragment_info)

        filterController = CourseFilterController(
            rootView = requireView(),
            scope = viewLifecycleOwner.lifecycleScope,
            onFilterChanged = { state ->
                viewModel.filterCourses(isMyCourseLib, model?.id, state.searchText, state.grade, state.subject, state.tagNames)
            },
            onScrollToTop = { scrollToTop() }
        )
        filterController.setup()

        selectionController = CourseSelectionController(
            rootView = requireView(),
            isMyCourseLib = isMyCourseLib,
            isGuest = userModel?.isGuest() ?: true,
            onRemoveConfirmed = {
                val courseIds = selectedItems?.mapNotNull { it?.courseId } ?: emptyList()
                viewLifecycleOwner.lifecycleScope.launch {
                    deleteSelected(true)
                    selectionController.clearAll(adapterCourses)
                    adapterCourses.removeCourses(courseIds)
                }
            },
            onArchiveConfirmed = {
                val courseIds = selectedItems?.mapNotNull { it?.courseId } ?: emptyList()
                viewLifecycleOwner.lifecycleScope.launch {
                    deleteSelected(true)
                    selectionController.clearAll(adapterCourses)
                    adapterCourses.removeCourses(courseIds)
                }
            },
            onAddToLib = {
                if ((selectedItems?.size ?: 0) > 0) {
                    confirmation = createAlertDialog()
                    confirmation.show()
                }
            },
            onSelectAllToggled = { isChecked ->
                if (::adapterCourses.isInitialized) {
                    adapterCourses.selectAllItems(isChecked)
                }
            }
        )
        selectionController.setup()
        checkList()
    }

    private fun setupButtonVisibility() {
        if (::selectionController.isInitialized) {
            selectionController.onListChanged(
                isEmpty = !::adapterCourses.isInitialized || adapterCourses.currentList.isEmpty(),
                hasSelectableItems = isMyCourseLib || (::adapterCourses.isInitialized && adapterCourses.currentList.any { !it.isMyCourse })
            )
        }
    }

    private fun setupEventListeners() {
        requireView().findViewById<View>(R.id.btn_collections).setOnClickListener {
            val f = CollectionsFragment.getInstance(filterController.searchTags, "courses")
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
                        arguments = Bundle().apply { putBoolean("isMyCourseLib", true) }
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

    private fun additionalSetup() {
        val bottomSheet = requireView().findViewById<View>(R.id.card_filter)
        requireView().findViewById<View>(R.id.filter).setOnClickListener {
            bottomSheet.visibility = if (bottomSheet.isVisible) View.GONE else View.VISIBLE
        }
        orderByDate = requireView().findViewById(R.id.order_by_date_button)
        orderByTitle = requireView().findViewById(R.id.order_by_title_button)
        orderByDate.isEnabled = false
        orderByTitle.isEnabled = false
        orderByDate.setOnClickListener {
            if (!::adapterCourses.isInitialized) return@setOnClickListener
            adapterCourses.toggleSortOrder { scrollToTop() }
        }
        orderByTitle.setOnClickListener {
            if (!::adapterCourses.isInitialized) return@setOnClickListener
            adapterCourses.toggleTitleSortOrder { scrollToTop() }
        }
    }

    private fun enableSortButtons() {
        if (::orderByDate.isInitialized) orderByDate.isEnabled = true
        if (::orderByTitle.isInitialized) orderByTitle.isEnabled = true
    }

    private fun checkList() {
        if (!::adapterCourses.isInitialized || !::filterController.isInitialized || !::selectionController.isInitialized) return
        val isEmpty = adapterCourses.currentList.isEmpty()
        filterController.setListVisible(!isEmpty)
        val hasSelectableItems = isMyCourseLib || adapterCourses.currentList.any { !it.isMyCourse }
        selectionController.onListChanged(isEmpty, hasSelectableItems)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnHomeItemClickListener) homeItemClickListener = context
    }

    override fun onSelectedListChange(list: MutableList<Course?>) {
        selectionJob?.cancel()
        selectionJob = viewLifecycleOwner.lifecycleScope.launch {
            val realmCourses = list.mapNotNull { course ->
                course?.let {
                    var rc = coursesRepository.getCourseById(it.courseId)
                    if (rc == null) {
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
                if (::selectionController.isInitialized && ::adapterCourses.isInitialized) {
                    selectionController.onSelectionChanged(realmCourses.size, adapterCourses.areAllSelected())
                }
            }
        }
    }

    override fun onTagClicked(tag: Tag) {
        val realmTag = RealmTag().apply {
            name = tag.name
            id = tag.id
        }
        onTagClicked(realmTag)
    }

    override fun onTagClicked(tag: RealmTag) {
        if (::filterController.isInitialized) filterController.addTag(tag)
    }

    override fun onTagSelected(tag: RealmTag) {
        if (::filterController.isInitialized) {
            filterController.setSingleTag(tag)
            showNoData(tvMessage, adapterCourses.itemCount, "courses")
        }
    }

    override fun onOkClicked(list: List<RealmTag>?) {
        if (::filterController.isInitialized) filterController.setTags(list ?: emptyList())
    }

    private fun createAlertDialog(): AlertDialog {
        val builder = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
        val msg = buildString {
            append(getString(R.string.success_you_have_added_the_following_courses))
            val itemsSize = selectedItems?.size ?: 0
            if (itemsSize <= 5) {
                selectedItems?.forEach { item -> append(" - ").append(item?.courseTitle).append(" \n") }
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
                        arguments = Bundle().apply { putBoolean("isMyCourseLib", true) }
                    }
                    homeItemClickListener?.openMyFragment(fragment)
                }
            }
            .setNegativeButton(R.string.ok) { dialog: DialogInterface, _: Int -> dialog.cancel() }
            .setOnDismissListener { addToMyList() }

        return builder.create()
    }

    private fun saveSearchActivity() {
        if (!::filterController.isInitialized || !filterController.filterApplied()) return
        val state = filterController.currentState()
        val tags = filterController.searchTags.toList()
        lifecycleScope.launch {
            coursesRepository.saveSearchActivity(
                state.searchText,
                "${model?.name}",
                "${model?.planetCode}",
                "${model?.parentCode}",
                tags,
                state.grade,
                state.subject
            )
        }
    }

    override fun onPause() {
        super.onPause()
        saveSearchActivity()
        if (::selectionController.isInitialized && ::adapterCourses.isInitialized) {
            selectionController.clearAll(adapterCourses)
        }
    }

    override fun onDestroy() {
        customProgressDialog?.dismiss()
        customProgressDialog = null
        super.onDestroy()
    }

    override fun getWatchedTables(): List<String> = listOf("courses")

    override fun onDataUpdated(table: String, update: TableDataUpdate) {
        if (table == "courses" && update.shouldRefreshUI) loadDataAsync()
    }

    override fun shouldAutoRefresh(table: String): Boolean = false

    override fun getSyncRecyclerView(): RecyclerView? {
        return if (::recyclerView.isInitialized) recyclerView else null
    }

    override fun onDestroyView() {
        if (::filterController.isInitialized) filterController.detach()
        super.onDestroyView()
    }

    override fun onRatingChanged() {
        if (!::adapterCourses.isInitialized) {
            super.onRatingChanged()
            return
        }
        if (::filterController.isInitialized) {
            val state = filterController.currentState()
            viewModel.filterCourses(isMyCourseLib, model?.id, state.searchText, state.grade, state.subject, state.tagNames)
            scrollToTop()
        }
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
