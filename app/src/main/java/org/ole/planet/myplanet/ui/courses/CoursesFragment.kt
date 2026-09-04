package org.ole.planet.myplanet.ui.courses

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.base.DefaultBaseAdapterFactory
import org.ole.planet.myplanet.callback.OnCourseItemSelectedListener
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnTagClickListener
import org.ole.planet.myplanet.model.Course
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.model.Tag
import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
import org.ole.planet.myplanet.ui.components.FragmentNavigator
import org.ole.planet.myplanet.ui.resources.CollectionsFragment
import org.ole.planet.myplanet.ui.sync.RealtimeSyncHelper
import org.ole.planet.myplanet.ui.sync.RealtimeSyncMixin
import org.ole.planet.myplanet.utils.DialogUtils
import org.ole.planet.myplanet.utils.GridSpanCalculator
import org.ole.planet.myplanet.utils.KeyboardUtils.setupUI
import org.ole.planet.myplanet.utils.ListViewMode
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.collectLatestWhenStarted

@AndroidEntryPoint
class CoursesFragment : BaseRecyclerFragment<MyCourse?>(), OnCourseItemSelectedListener, OnTagClickListener, RealtimeSyncMixin {
    override val shouldShowDownloadDialog = false
    private lateinit var adapterCourses: CoursesAdapter
    private var orderByDate: Button? = null
    private var orderByTitle: Button? = null
    private lateinit var filterController: CourseFilterController
    private lateinit var selectionController: CourseSelectionController
    private var toggleGridButton: ImageButton? = null
    private var toggleListButton: ImageButton? = null
    var userModel: UserEntity? = null
    private lateinit var confirmation: AlertDialog
    private var selectionJob: Job? = null
    private val refreshJobs = mutableMapOf<String, Job>()
    private var pendingScrollState: Parcelable? = null
    private val viewModel: CoursesViewModel by viewModels()
    @Inject
    lateinit var userSessionManager: UserSessionManager

    @Inject
    lateinit var realtimeSyncManager: RealtimeSyncManager

    private lateinit var realtimeSyncHelper: RealtimeSyncHelper

    private val spanUpdateRunnable = Runnable { updateGridSpanIfNeeded() }
    private val layoutChangeListener = View.OnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
        if (right - left != oldRight - oldLeft) {
            recyclerView.removeCallbacks(spanUpdateRunnable)
            recyclerView.post(spanUpdateRunnable)
        }
    }

    override fun getLayout(): Int = R.layout.fragment_my_course

    private fun scrollToTop() {
        recyclerView.post {
            if ((recyclerView.adapter?.itemCount ?: 0) > 0) recyclerView.scrollToPosition(0)
        }
    }

    private fun loadDataAsync() {
        val hostActivity = activity ?: return
        if (hostActivity.isFinishing) return
        if (::recyclerView.isInitialized) {
            pendingScrollState = recyclerView.layoutManager?.onSaveInstanceState()
        }
        viewModel.loadCourses(isMyCourseLib, model?.id)
    }

    override suspend fun postAddRefresh() {
        loadDataAsync()
    }

    override fun deleteSelected(deleteProgress: Boolean) {
        val userId = userModel?.id ?: return
        val snapshot = selectedItems?.filterNotNull() ?: return
        if (snapshot.isEmpty()) return
        val courseIds = snapshot.mapNotNull { it.courseId.takeIf { id -> !id.isNullOrBlank() } ?: it.id.takeIf { id -> !id.isNullOrBlank() } ?: it._id }
        viewModel.removeCourses(courseIds, userId, deleteProgress) {
            if (isAdded) {
                selectedItems?.clear()
                Utilities.toast(activity, getString(R.string.removed_from_mycourse))
                viewModel.loadCourses(isMyCourseLib, model?.id)
            }
        }
    }

    override suspend fun getAdapter(): ListAdapter<*, *> {
        val hostActivity = activity ?: throw CancellationException("Fragment detached")

        if (userModel == null) {
            userModel = userSessionManager.getUserModel()
        }

        // The adapter caches the Context (Activity) which outlives onCreateView,
        // but Fragments and their host Activities are re-created together so this is safe from leaks.
        if (!::adapterCourses.isInitialized) {
            val factory = adapterFactory ?: DefaultBaseAdapterFactory()
            adapterCourses = factory.createCoursesAdapter(
                context = hostActivity,
                isGuest = userModel?.isGuest() ?: true,
                isMyCourseLib = isMyCourseLib,
                viewMode = sharedPrefManager.getCourseViewMode()
            )
        } else {
            adapterCourses.setViewMode(sharedPrefManager.getCourseViewMode())
            adapterCourses.updateIdentity(userModel?.isGuest() ?: true)
        }

        adapterCourses.setListener(this@CoursesFragment)
        enableSortButtons()

        val cachedState = viewModel.coursesState.value
        if (cachedState.courses.isNotEmpty()) {
            adapterCourses.setProgressMap(cachedState.progressMap)
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

        toggleGridButton = view.findViewById(R.id.toggle_grid)
        toggleListButton = view.findViewById(R.id.toggle_list)

        additionalSetup()
        setupMyProgressButton()
        setupViewModeToggle()
        setupCourseFilterChips()

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
            checkList()
        }

        collectLatestWhenStarted(viewModel.coursesState) { state ->
            if (!::adapterCourses.isInitialized) return@collectLatestWhenStarted

                if (isMyCourseLib) {
                    val courseIds = state.courses.map { it.courseId }
                    resources = coursesRepository.getCourseOfflineResources(courseIds)
                    courseLib = "courses"
                }

                adapterCourses.setProgressMap(state.progressMap)
                adapterCourses.submitList(state.courses) {
                    if (isAdded && ::selectionController.isInitialized) {
                        selectedItems?.clear()
                        selectionController.clearAll(adapterCourses)
                        checkList()
                        showNoData(tvMessage, state.courses.size, "courses")
                        pendingScrollState?.let { saved ->
                            recyclerView.layoutManager?.onRestoreInstanceState(saved)
                            pendingScrollState = null
                        }
                    }
                }
            }

        realtimeSyncHelper = RealtimeSyncHelper(this, this, realtimeSyncManager)
        realtimeSyncHelper.setupRealtimeSync()
    }

    private fun initializeView() {
        tvMessage = requireView().findViewById(R.id.tv_message)
        requireView().findViewById<View>(R.id.tl_tags).visibility = View.GONE
        tvFragmentInfo = requireView().findViewById(R.id.tv_fragment_info)

        filterController = CourseFilterController(
            rootView = requireView(),
            coroutineScope = viewLifecycleOwner.lifecycleScope,
            onScrollToTop = { scrollToTop() }
        )
        filterController.setup()

        var lastState: FilterState? = null
        var isFirstEmission = true
        collectLatestWhenStarted(filterController.filterState) { state ->
            if (isFirstEmission) {
                isFirstEmission = false
                if (!state.isActive) {
                    lastState = state
                    return@collectLatestWhenStarted
                }
            }
            if (state == lastState) return@collectLatestWhenStarted

            if (lastState != null && state.searchText != lastState?.searchText && state.copy(searchText = "") == lastState?.copy(searchText = "")) {
                delay(300.milliseconds)
            }
            lastState = state
            viewModel.filterCourses(
                isMyCourseLib, model?.id, state.searchText, state.grade,
                state.subject, state.tagNames, state.progressFilter
            )
        }

        selectionController = CourseSelectionController(
            rootView = requireView(),
            isMyCourseLib = isMyCourseLib,
            isGuest = userModel?.isGuest() ?: true,
            onRemoveConfirmed = {
                val courseIds = selectedItems?.mapNotNull { it?.courseId } ?: emptyList()
                deleteSelected(true)
                selectionController.clearAll(adapterCourses)
                adapterCourses.removeCourses(courseIds) {
                    checkList()
                }
            },
            onArchiveConfirmed = {
                val courseIds = selectedItems?.mapNotNull { it?.courseId } ?: emptyList()
                deleteSelected(true)
                selectionController.clearAll(adapterCourses)
                adapterCourses.removeCourses(courseIds) {
                    checkList()
                }
            },
            onAddToLib = {
                if (!selectedItems.isNullOrEmpty()) {
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
            val isEmpty = !::adapterCourses.isInitialized || adapterCourses.currentList.isEmpty()
            val hasSelectableItems = if (isMyCourseLib) !isEmpty else (::adapterCourses.isInitialized && adapterCourses.currentList.any { !it.isMyCourse })
            selectionController.onListChanged(
                isEmpty = isEmpty,
                hasSelectableItems = hasSelectableItems
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
        requireView().findViewById<View>(R.id.btn_close_filter)?.setOnClickListener {
            bottomSheet.visibility = View.GONE
        }
        requireView().findViewById<View>(R.id.btn_collections)?.setOnClickListener {
            bottomSheet.visibility = View.GONE
        }
        requireView().findViewById<View>(R.id.btn_clear_tags)?.setOnClickListener {
            bottomSheet.visibility = View.GONE
        }
        orderByDate = requireView().findViewById(R.id.order_by_date_button)
        orderByTitle = requireView().findViewById(R.id.order_by_title_button)
        orderByDate?.isEnabled = false
        orderByTitle?.isEnabled = false
        orderByDate?.setOnClickListener {
            bottomSheet.visibility = View.GONE
            if (!::adapterCourses.isInitialized) return@setOnClickListener
            viewModel.toggleDateSort()
            scrollToTop()
        }
        orderByTitle?.setOnClickListener {
            bottomSheet.visibility = View.GONE
            if (!::adapterCourses.isInitialized) return@setOnClickListener
            viewModel.toggleTitleSort()
            scrollToTop()
        }
    }

    private fun enableSortButtons() {
        orderByDate?.isEnabled = true
        orderByTitle?.isEnabled = true
    }

    private fun setupViewModeToggle() {
        updateToggleUi(sharedPrefManager.getCourseViewMode())
        toggleGridButton?.setOnClickListener { setViewMode(ListViewMode.GRID) }
        toggleListButton?.setOnClickListener { setViewMode(ListViewMode.LIST) }
        recyclerView.addOnLayoutChangeListener(layoutChangeListener)
    }

    private fun setViewMode(mode: ListViewMode) {
        sharedPrefManager.setCourseViewMode(mode)
        updateToggleUi(mode)
        if (::adapterCourses.isInitialized) {
            adapterCourses.setViewMode(mode)
        }
    }

    private fun applyRecyclerLayoutManager(mode: ListViewMode) {
        val currentLayoutManager = recyclerView.layoutManager
        if (mode == ListViewMode.GRID) {
            if (currentLayoutManager is GridLayoutManager) {
                currentLayoutManager.spanCount = currentSpanCount()
            } else {
                recyclerView.layoutManager = GridLayoutManager(requireContext(), currentSpanCount())
            }
        } else {
            if (currentLayoutManager !is LinearLayoutManager || currentLayoutManager is GridLayoutManager) {
                recyclerView.layoutManager = LinearLayoutManager(requireContext())
            }
        }
    }

    private fun currentSpanCount(): Int {
        val displayMetrics = requireContext().resources.displayMetrics
        val widthPx = recyclerView.width.takeIf { it > 0 } ?: displayMetrics.widthPixels
        val widthDp = (widthPx / displayMetrics.density).toInt()
        return GridSpanCalculator.columnCount(widthDp)
    }

    private fun updateGridSpanIfNeeded() {
        val layoutManager = recyclerView.layoutManager
        if (layoutManager is GridLayoutManager) {
            val currentSpan = currentSpanCount()
            if (layoutManager.spanCount != currentSpan) {
                layoutManager.spanCount = currentSpan
            }
        }
    }

    private fun updateToggleUi(mode: ListViewMode) {
        val isGrid = mode == ListViewMode.GRID
        val activeColor = ContextCompat.getColor(requireContext(), android.R.color.white)
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.daynight_textColor)
        toggleGridButton?.setBackgroundResource(if (isGrid) R.drawable.bg_toggle_selected else android.R.color.transparent)
        toggleListButton?.setBackgroundResource(if (!isGrid) R.drawable.bg_toggle_selected else android.R.color.transparent)
        toggleGridButton?.let { ImageViewCompat.setImageTintList(it, ColorStateList.valueOf(if (isGrid) activeColor else inactiveColor)) }
        toggleListButton?.let { ImageViewCompat.setImageTintList(it, ColorStateList.valueOf(if (!isGrid) activeColor else inactiveColor)) }
        applyRecyclerLayoutManager(mode)
    }

    private fun setupCourseFilterChips() {
        val chipRow = requireView().findViewById<LinearLayout>(R.id.chip_filter_row)
        chipRow.removeAllViews()
        val options = requireContext().resources.getStringArray(R.array.progress_filter)
        options.forEach { label ->
            val chip = layoutInflater.inflate(R.layout.item_filter_chip, chipRow, false) as TextView
            chip.text = label
            chip.tag = label
            chip.setOnClickListener {
                if (::filterController.isInitialized) {
                    filterController.setProgressFilter(if (label == options.first()) "" else label)
                }
                renderCourseChipSelection(chipRow)
            }
            chipRow.addView(chip)
        }
        renderCourseChipSelection(chipRow)
    }

    private fun renderCourseChipSelection(chipRow: LinearLayout) {
        val selected = if (::filterController.isInitialized) filterController.currentState().progressFilter else ""
        for (i in 0 until chipRow.childCount) {
            val chip = chipRow.getChildAt(i) as? TextView ?: continue
            val isSelected = (chip.tag as? String)?.let { it == selected || (selected.isEmpty() && i == 0) } == true
            chip.setBackgroundResource(if (isSelected) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
            chip.setTextColor(
                ContextCompat.getColor(requireContext(), if (isSelected) R.color.chip_selected_text else R.color.daynight_textColor)
            )
        }
    }

    private fun checkList() {
        if (!::adapterCourses.isInitialized || !::filterController.isInitialized || !::selectionController.isInitialized) return
        val isEmpty = adapterCourses.currentList.isEmpty()
        filterController.setListVisible(!isEmpty || filterController.filterApplied())
        val hasSelectableItems = if (isMyCourseLib) !isEmpty else adapterCourses.currentList.any { !it.isMyCourse }
        selectionController.onListChanged(isEmpty, hasSelectableItems)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnHomeItemClickListener) homeItemClickListener = context
    }

    override fun onSelectedListChange(list: MutableList<Course?>) {
        val myCourses = list.mapNotNull { course ->
            course?.let {
                MyCourse().apply {
                    id = it.courseId
                    _id = it.courseId
                    courseId = it.courseId
                    courseTitle = it.courseTitle
                    isMyCourse = it.isMyCourse
                }
            }
        }.toMutableList<MyCourse?>()

        selectedItems = myCourses
        if (::selectionController.isInitialized && ::adapterCourses.isInitialized) {
            selectionController.onSelectionChanged(myCourses.size, adapterCourses.areAllSelected())
        }
    }

    override fun onTagClicked(tag: Tag) {
        val realmTag = TagEntity().apply {
            name = tag.name
            id = tag.id.orEmpty()
        }
        onTagClicked(realmTag)
    }

    override fun onTagClicked(tag: TagEntity) {
        if (::filterController.isInitialized) filterController.addTag(tag)
    }

    override fun onTagSelected(tag: TagEntity) {
        if (::filterController.isInitialized) {
            filterController.setSingleTag(tag)
            showNoData(tvMessage, adapterCourses.itemCount, "courses")
        }
    }

    override fun onOkClicked(list: List<TagEntity>?) {
        if (::filterController.isInitialized) filterController.setTags(list ?: emptyList())
    }

    override fun addToMyList(onComplete: (() -> Unit)?) {
        super.addToMyList {
            if (isAdded && ::selectionController.isInitialized && ::adapterCourses.isInitialized) {
                selectionController.clearAll(adapterCourses)
            }
            onComplete?.invoke()
        }
    }

    private fun createAlertDialog(): AlertDialog {
        var hasAdded = false
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
                    DialogUtils.guestDialog(requireContext())
                } else {
                    hasAdded = true
                    addToMyList {
                        val fragment = CoursesFragment().apply {
                            arguments = Bundle().apply { putBoolean("isMyCourseLib", true) }
                        }
                        homeItemClickListener?.openMyFragment(fragment)
                    }
                }
            }
            .setNegativeButton(R.string.ok) { dialog: DialogInterface, _: Int ->
                hasAdded = true
                addToMyList()
                dialog.cancel()
            }
            .setOnDismissListener {
                if (!hasAdded) {
                    addToMyList()
                }
            }

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

    override fun onResume() {
        super.onResume()
        if (::recyclerView.isInitialized) {
            recyclerView.removeCallbacks(spanUpdateRunnable)
            recyclerView.post(spanUpdateRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        saveSearchActivity()
        if (::selectionController.isInitialized && ::adapterCourses.isInitialized) {
            selectionController.clearAll(adapterCourses)
        }
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
        if (::recyclerView.isInitialized) {
            recyclerView.removeOnLayoutChangeListener(layoutChangeListener)
            recyclerView.removeCallbacks(spanUpdateRunnable)
        }
        if (::filterController.isInitialized) {
            filterController.detach()
        }
        if (::adapterCourses.isInitialized) {
            adapterCourses.setListener(null)
        }
        toggleGridButton = null
        toggleListButton = null
        orderByDate = null
        orderByTitle = null
        super.onDestroyView()
    }

    override fun onRatingChanged() {
        if (!::adapterCourses.isInitialized) {
            return
        }
        if (::filterController.isInitialized) {
            val state = filterController.currentState()
            viewModel.filterCourses(isMyCourseLib, model?.id, state.searchText, state.grade, state.subject, state.tagNames, state.progressFilter)
            scrollToTop()
        }
    }

    override fun onRatingChanged(type: String, id: String) {
        if (type == "course" && ::adapterCourses.isInitialized) {
            refreshJobs[id]?.cancel()
            refreshJobs[id] = viewLifecycleOwner.lifecycleScope.launch {
                adapterCourses.notifyItemChangedById(id)
            }
        }
    }

}
