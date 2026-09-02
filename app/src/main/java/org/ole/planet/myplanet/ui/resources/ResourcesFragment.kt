package org.ole.planet.myplanet.ui.resources

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.base.DefaultBaseAdapterFactory
import org.ole.planet.myplanet.callback.OnFilterListener
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnLibraryItemSelectedListener
import org.ole.planet.myplanet.callback.OnTagClickListener
import org.ole.planet.myplanet.databinding.FragmentMyLibraryBinding
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.ResourceItem
import org.ole.planet.myplanet.model.ResourceListModel
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.model.TagItem
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.sync.RealtimeSyncHelper
import org.ole.planet.myplanet.ui.sync.RealtimeSyncMixin
import org.ole.planet.myplanet.utils.DialogUtils.guestDialog
import org.ole.planet.myplanet.utils.GridSpanCalculator
import org.ole.planet.myplanet.utils.KeyboardUtils.setupUI
import org.ole.planet.myplanet.utils.ListViewMode
import org.ole.planet.myplanet.utils.ResourcesSearchUtils
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.collectWhenStarted
import org.ole.planet.myplanet.utils.textChanges

@AndroidEntryPoint
class ResourcesFragment : BaseRecyclerFragment<MyLibrary?>(), OnLibraryItemSelectedListener,
    OnTagClickListener, OnFilterListener, RealtimeSyncMixin {
    private var _binding: FragmentMyLibraryBinding? = null
    private val binding get() = _binding!!
    private val tvAddToLib get() = binding.tvAdd
    private val tvSelected get() = binding.tvSelected
    private val layoutSearch get() = binding.layoutSearch.root
    private val etSearch get() = binding.layoutSearch.etSearch
    private val flexBoxTags get() = binding.layoutSearch.flexboxTags
    private val clearTags get() = binding.btnClearTags
    private val selectAll get() = binding.selectAll
    private val filter get() = binding.filter
    private var layoutViewToggle: View? = null
    private var toggleGridButton: ImageButton? = null
    private var toggleListButton: ImageButton? = null
    private lateinit var searchTags: MutableList<TagEntity>
    private lateinit var adapterLibrary: ResourcesAdapter
    var userModel: UserEntity ?= null
    var map: HashMap<String?, JsonObject>? = null
    private var confirmation: AlertDialog? = null
    private var allResourceModels: List<ResourceListModel> = emptyList()

    private var lastSearchQuery: String? = null
    private var lastSearchTags: List<String>? = null
    private var lastSubjects: Set<String>? = null
    private var lastLevels: Set<String>? = null
    private var lastLanguages: Set<String>? = null
    private var lastMediums: Set<String>? = null
    @Inject
    lateinit var prefManager: SharedPrefManager

    private val viewModel: ResourcesViewModel by viewModels()
    @Inject
    lateinit var realtimeSyncManager: RealtimeSyncManager
    private var selectedDownloadFilterIndex: Int = 0   // 0 = All
    private var lastDownloadFilterIndex: Int = 0
    private lateinit var realtimeSyncHelper: RealtimeSyncHelper
    private var refreshJob: Job? = null
    private var searchJob: Job? = null

    private val spanUpdateRunnable = Runnable { updateGridSpanIfNeeded() }
    private val layoutChangeListener = View.OnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
        if (right - left != oldRight - oldLeft) {
            recyclerView.removeCallbacks(spanUpdateRunnable)
            recyclerView.post(spanUpdateRunnable)
        }
    }

    internal val addResourceLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            refreshResourcesData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun getLayout(): Int {
        return R.layout.fragment_my_library
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        _binding = view?.let { FragmentMyLibraryBinding.bind(it) }
        return view
    }

    override fun onRatingChanged(type: String, id: String) {
        refreshResourcesData()
    }

    private fun refreshResourcesData() {
        if (!isAdded || requireActivity().isFinishing) return
        if (view == null) return
        refreshJob?.cancel()
        refreshJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                allResourceModels = viewModel.getLibraryListModels(isMyCourseLib, model?.id)
                lastSearchQuery = null
                applyFiltersAndUpdateUI(scrollToTop = false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override suspend fun getAdapter(): ListAdapter<*, *> {
        allResourceModels = viewModel.getLibraryListModels(isMyCourseLib, model?.id)

        val user = viewModel.getCurrentUser()
        // The adapter caches the Context (Activity) which outlives onCreateView,
        // but Fragments and their host Activities are re-created together so this is safe from leaks.
        if (!::adapterLibrary.isInitialized) {
            val factory = adapterFactory ?: DefaultBaseAdapterFactory()
            adapterLibrary = factory.createResourcesAdapter(
                context = requireActivity(),
                isGuest = user?.isGuest() == true,
                openedResourceIds = emptySet(),
                currentUserName = user?.name,
                viewMode = prefManager.getLibraryViewMode(),
                dispatcherProvider = dispatcherProvider,
                onEditClick = { model -> openEditResource(model) }
            )
        } else {
            adapterLibrary.setViewMode(prefManager.getLibraryViewMode())
            adapterLibrary.updateIdentity(user?.isGuest() == true, user?.name)
        }

        adapterLibrary.setListener(this)

        val filteredList = applyFilterModels(filterLocalLibraryByTag(allResourceModels, etSearch.text?.toString()?.trim().orEmpty(), searchTags))
        adapterLibrary.setLibraryList(filteredList)

        checkList(filteredList.size)
        showNoData(tvMessage, filteredList.size, "resources")
        changeButtonStatus()
        return adapterLibrary
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toggleGridButton = view.findViewById(R.id.toggle_grid)
        toggleListButton = view.findViewById(R.id.toggle_list)
        layoutViewToggle = view.findViewById<View>(R.id.layout_view_toggle) ?: (toggleGridButton?.parent as? View)
        isMyCourseLib = arguments?.getBoolean("isMyCourseLib", false) ?: false
        searchTags = ArrayList()

        initializeViews()
        setupEventListeners()
        initArrays()
        hideButton()

        setupDownloadFilterChips()

        childFragmentManager.setFragmentResultListener("resource_added", viewLifecycleOwner) { _, _ ->
            refreshResourcesData()
        }

        collectWhenStarted(viewModel.downloadComplete) { completed ->
            if (completed) {
                refreshResourcesData()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                broadcastService.events.collect { intent ->
                    if (intent.action == DashboardActivity.MESSAGE_PROGRESS) {
                        val download = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra("download", Download::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra("download")
                        }
                        if (download?.completeAll == true) {
                            viewModel.notifyDownloadComplete()
                        }
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentUser.filterNotNull().collectLatest { user ->
                    userModel = user
                    if (::adapterLibrary.isInitialized && _binding != null) {
                        checkList()
                    }
                    val userId = userModel?.id
                    if (userId != null) {
                        viewModel.observeOpenedResourceIds(userId)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.openedResourceIds.collectLatest { openedResourceIds ->
                    if (::adapterLibrary.isInitialized) {
                        adapterLibrary.setOpenedResourceIds(openedResourceIds)
                    }
                }
            }
        }

        if (::adapterLibrary.isInitialized) {
            showNoData(tvMessage, adapterLibrary.itemCount, "resources")
            changeButtonStatus()
            checkList()
        }
        clearTagsButton()
        setupUI(binding.myLibraryParentLayout, requireActivity())
        additionalSetup()
        setupViewModeToggle()

        tvFragmentInfo = binding.tvFragmentInfo
        if (isMyCourseLib) tvFragmentInfo.setText(R.string.txt_myLibrary)

        realtimeSyncHelper = RealtimeSyncHelper(this, this, realtimeSyncManager)
        realtimeSyncHelper.setupRealtimeSync()
    }

    private fun setupViewModeToggle() {
        updateToggleUi(prefManager.getLibraryViewMode())
        toggleGridButton?.setOnClickListener { setViewMode(ListViewMode.GRID) }
        toggleListButton?.setOnClickListener { setViewMode(ListViewMode.LIST) }
        recyclerView.addOnLayoutChangeListener(layoutChangeListener)
    }

    private fun setViewMode(mode: ListViewMode) {
        prefManager.setLibraryViewMode(mode)
        updateToggleUi(mode)
        if (::adapterLibrary.isInitialized) {
            adapterLibrary.setViewMode(mode)
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

    private fun initializeViews() {
        if (tvSelected.text.isNullOrEmpty()) {
            tvSelected.visibility = View.GONE
        } else {
            tvSelected.visibility = View.VISIBLE
        }
    }

    private fun openEditResource(model: ResourceListModel) {
        val intent = android.content.Intent(requireContext(), AddResourceActivity::class.java).apply {
            putExtra("resource_id", model.library.id)
            putExtra("resource_local_url", model.library.resourceLocalAddress)
            putExtra("is_edit_mode", true)
        }
        startActivity(intent)
    }

    private fun setupEventListeners() {
        setupAddToLibListener()
        setupDeleteListener()
        setupSearchTextListener()
        setupCollectionsButton()
        setupSelectAllListener()
        setupAddResourceButtonListener()
    }

    private fun setupAddToLibListener() {
        tvAddToLib.setOnClickListener {
            if ((selectedItems?.size ?: 0) > 0) {
                confirmation = createAlertDialog()
                confirmation?.show()
            }
        }
    }

    private fun setupDeleteListener() {
        tvDelete?.setOnClickListener {
            AlertDialog.Builder(this.context, R.style.AlertDialogTheme)
                .setMessage(R.string.confirm_removal)
                .setPositiveButton(R.string.yes) { _, _ ->
                    deleteSelected(true)
                }
                .setNegativeButton(R.string.no, null).show()
        }
    }

    private fun setupSearchTextListener() {
        etSearch.textChanges()
            .debounce(300L)
            .distinctUntilChanged()
            .onEach {
                if (!::adapterLibrary.isInitialized || !isAdded || _binding == null) return@onEach
                applyFiltersAndUpdateUI()
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun applyFiltersAndUpdateUI(scrollToTop: Boolean = true) {
        if (!::adapterLibrary.isInitialized || !isAdded || _binding == null) return
        val searchQuery = etSearch.text?.toString()?.trim().orEmpty()

        val currentSearchTags = if (::searchTags.isInitialized) searchTags else emptyList()
        val searchTagIds = currentSearchTags.map { it.id }.sorted()

        if (searchQuery == lastSearchQuery &&
            searchTagIds == lastSearchTags &&
            subjects == lastSubjects &&
            levels == lastLevels &&
            languages == lastLanguages &&
            mediums == lastMediums &&
            selectedDownloadFilterIndex == lastDownloadFilterIndex
        ) {
            return
        }

        lastDownloadFilterIndex = selectedDownloadFilterIndex

        lastSearchQuery = searchQuery
        lastSearchTags = searchTagIds
        lastSubjects = HashSet(subjects)
        lastLevels = HashSet(levels)
        lastLanguages = HashSet(languages)
        lastMediums = HashSet(mediums)

        val filteredList = applyFilterModels(filterLocalLibraryByTag(allResourceModels, searchQuery, currentSearchTags))

        if (scrollToTop) {
            adapterLibrary.setLibraryList(filteredList) {
                recyclerView.scrollToPosition(0)
            }
        } else {
            adapterLibrary.setLibraryList(filteredList)
        }

        checkList(filteredList.size)
        showNoData(tvMessage, filteredList.size, "resources")
    }

    private fun setupCollectionsButton() {
        binding.btnCollections.setOnClickListener {
            val f = CollectionsFragment.getInstance(searchTags, "resources")
            f.setListener(this@ResourcesFragment)
            f.show(childFragmentManager, "")
        }
    }

    private fun setupSelectAllListener() {
        selectAll.setOnClickListener {
            hideButton()
            val allSelected = adapterLibrary.areAllSelected()
            adapterLibrary.selectAllItems(!allSelected)
            if (allSelected) {
                selectAll.isChecked = false
                selectAll.text = getString(R.string.select_all)
            } else {
                selectAll.isChecked = true
                selectAll.text = getString(R.string.unselect_all)
            }
        }
    }

    private fun setupAddResourceButtonListener() {
        binding.addResource.visibility = if (isMyCourseLib) View.VISIBLE else View.GONE
        binding.addResource.setOnClickListener {
            if (userModel?.id?.startsWith("guest") == false) {
                AddResourceFragment().show(childFragmentManager, getString(R.string.add_res))
            } else {
                guestDialog(requireContext())
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnHomeItemClickListener) {
            homeItemClickListener = context
        }
    }

    private fun hideButton(){
        val count = selectedItems?.size ?: 0
        tvDelete?.isEnabled = count != 0
        tvAddToLib.isEnabled = count != 0
        if(count != 0 && userModel?.isGuest() != true){
            if(isMyCourseLib) tvDelete?.visibility = View.VISIBLE
            else tvAddToLib.visibility = View.VISIBLE
        } else {
            if(isMyCourseLib) tvDelete?.visibility = View.GONE
            else tvAddToLib.visibility = View.GONE
        }
    }

    private fun checkList(listSize: Int = if (::adapterLibrary.isInitialized) adapterLibrary.currentList.size else 0) {
        val hasAnyLibraryData = allResourceModels.isNotEmpty()
        val isGuest = userModel?.isGuest() == true
        val scrollChipFilter = binding.root.findViewById<View>(R.id.scroll_chip_filter)

        if (!hasAnyLibraryData && listSize == 0) {
            selectAll.visibility = View.GONE
            layoutSearch.visibility = View.GONE
            layoutViewToggle?.visibility = View.GONE
            tvSelected.visibility = View.GONE
            binding.btnCollections.visibility = View.GONE
            filter.visibility = View.GONE
            clearTags.visibility = View.GONE
            tvDelete?.visibility = View.GONE
            scrollChipFilter?.visibility = View.GONE
        } else {
            selectAll.visibility = if (isGuest) View.GONE else View.VISIBLE
            layoutSearch.visibility = View.VISIBLE
            layoutViewToggle?.visibility = View.VISIBLE
            binding.btnCollections.visibility = View.VISIBLE
            filter.visibility = View.VISIBLE
            clearTags.visibility = if (hasActiveFilters()) View.VISIBLE else View.GONE
            scrollChipFilter?.visibility = View.VISIBLE
        }
        hideButton()
    }

    private fun hasActiveFilters(): Boolean =
        etSearch.text?.isNotBlank() == true ||
                searchTags.isNotEmpty() ||
                subjects.isNotEmpty() ||
                levels.isNotEmpty() ||
                languages.isNotEmpty() ||
                mediums.isNotEmpty() ||
                selectedDownloadFilterIndex != 0

    private fun initArrays() {
        subjects = HashSet()
        languages = HashSet()
        levels = HashSet()
        mediums = HashSet()
    }

    private fun createAlertDialog(): AlertDialog {
        var hasAdded = false
        val builder = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
        builder.setMessage(buildAlertMessage())
        builder.setCancelable(true)
            .setPositiveButton(R.string.go_to_mylibrary) { _: DialogInterface, _: Int ->
                if (userModel?.id?.startsWith("guest") == true) {
                    guestDialog(requireContext())
                } else {
                    hasAdded = true
                    addToMyList {
                        val fragment = ResourcesFragment().apply {
                            arguments = Bundle().apply {
                                putBoolean("isMyCourseLib", true)
                            }
                        }
                        homeItemClickListener?.openMyFragment(fragment)
                    }
                }
            }
        builder.setNegativeButton(getString(R.string.ok)) { dialog: DialogInterface, _: Int ->
            hasAdded = true
            addToMyList()
            dialog.cancel()
        }
        builder.setOnDismissListener {
            if (!hasAdded) {
                addToMyList()
            }
        }
        return builder.create()
    }

    private fun buildAlertMessage(): String {
        return buildString {
            append(getString(R.string.success_you_have_added_these_resources_to_your_mylibrary))
            val itemsSize = selectedItems?.size ?: 0
            if (itemsSize <= 5) {
                selectedItems?.forEach { item ->
                    append(" - ").append(item?.title).append("\n")
                }
            } else {
                for (i in 0..4) {
                    append(" - ").append(selectedItems?.get(i)?.title).append("\n")
                }
                append(getString(R.string.and)).append(itemsSize - 5)
                    .append(getString(R.string.more_resource_s))
            }
            append(getString(R.string.return_to_the_home_tab_to_access_mylibrary))
            append(getString(R.string.note_you_may_still_need_to_download_the_newly_added_resources))
        }
    }

    private fun clearTagsButton() {
        clearTags.setOnClickListener {
            binding.cardFilter.visibility = View.GONE
            saveSearchActivity()
            selectedDownloadFilterIndex = 0
            val chipRow = binding.chipFilterRow
            if (chipRow != null) {
                renderDownloadChipSelection(chipRow)
            }
            searchTags.clear()
            flexBoxTags.removeAllViews()
            etSearch.setText(R.string.empty_text)
            tvSelected.text = getString(R.string.empty_text)
            levels.clear()
            mediums.clear()
            subjects.clear()
            languages.clear()
            searchJob?.cancel()
            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                applyFiltersAndUpdateUI()
            }
        }
    }

    override fun onSelectedListChange(list: List<ResourceItem>) {
        val newSelected = list.mapNotNull { item ->
            allResourceModels.find { it.item.id == item.id }?.library
        }
        selectedItems?.clear()
        selectedItems?.addAll(newSelected)
        changeButtonStatus()
        hideButton()
    }

    override fun onTagClicked(tag: TagItem) {
        val realmTag = allResourceModels.flatMap { it.tags }.find { it.id == tag.id }
        if (realmTag != null) {
            val rTag = searchTags.find { it.id == realmTag.id } ?: TagEntity().apply {
                id = realmTag.id.orEmpty()
                name = realmTag.name
            }
            onTagClicked(rTag)
        }
    }

    override fun onSingleResourceDownloaded(url: String) {
        if (!::adapterLibrary.isInitialized) return
        val localAddress = url.substringAfterLast('/')
        val id = allResourceModels.find { it.item.resourceLocalAddress == localAddress }?.item?.id ?: return
        adapterLibrary.markItemAsOffline(id)
        refreshResourcesData()
    }

    override fun onResourceClicked(item: ResourceItem) {
        val model = allResourceModels.find { it.item.id == item.id }
        if (model != null) {
            homeItemClickListener?.openLibraryDetailFragment(model.library)
        }
    }

    override fun onTagClicked(tag: TagEntity) {
        tvSelected.visibility = View.VISIBLE
        if (!searchTags.any { it.name == tag.name }) searchTags.add(tag)
        renderTagChips()
        showTagText(searchTags, tvSelected)
        searchJob?.cancel()
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            applyFiltersAndUpdateUI()
        }
    }

    private fun renderTagChips() {
        val context = context ?: return
        val chipContext = ContextThemeWrapper(context, R.style.Theme_App_Chip)
        flexBoxTags.removeAllViews()
        for (tag in searchTags) {
            val chip = Chip(chipContext).apply {
                text = tag.name
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    searchTags.remove(tag)
                    renderTagChips()
                    showTagText(searchTags, tvSelected)
                    searchJob?.cancel()
                    searchJob = viewLifecycleOwner.lifecycleScope.launch {
                        applyFiltersAndUpdateUI()
                    }
                }
            }
            flexBoxTags.addView(chip)
        }
    }

    override fun onTagSelected(tag: TagEntity) {
        tvSelected.visibility = View.VISIBLE
        val li: MutableList<TagEntity> = ArrayList()
        li.add(tag)
        searchTags = li
        tvSelected.text = getString(R.string.tag_selected, tag.name)
        searchJob?.cancel()
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            applyFiltersAndUpdateUI()
        }
    }

    override fun onOkClicked(list: List<TagEntity>?) {
        if (list?.isEmpty() == true) {
            searchTags.clear()
            flexBoxTags.removeAllViews()
            searchJob?.cancel()
            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                applyFiltersAndUpdateUI()
            }
        } else {
            for (tag in list ?: emptyList()) {
                if (!searchTags.any { it.name == tag.name }) searchTags.add(tag)
            }
            tvSelected.visibility = View.VISIBLE
            renderTagChips()
            showTagText(searchTags, tvSelected)
            searchJob?.cancel()
            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                applyFiltersAndUpdateUI()
            }
        }
    }

    private fun changeButtonStatus() {
        if (adapterLibrary.areAllSelected()) {
            selectAll.isChecked = true
            selectAll.text = getString(R.string.unselect_all)
        } else {
            selectAll.isChecked = false
            selectAll.text = getString(R.string.select_all)
        }
    }

    override fun filter(subjects: MutableSet<String>, languages: MutableSet<String>, mediums: MutableSet<String>, levels: MutableSet<String>) {
        this.subjects = subjects
        this.languages = languages
        this.mediums = mediums
        this.levels = levels
        searchJob?.cancel()
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            applyFiltersAndUpdateUI()
        }
    }

    override suspend fun getData(): Map<String, Set<String>> {
        // Keep facet options stable so applying one filter does not hide other available options.
        return viewModel.getFilterFacets(allResourceModels.map { it.library })
    }

    override fun getSelectedFilter(): Map<String, Set<String>> {
        val b: MutableMap<String, Set<String>> = HashMap()
        b["languages"] = languages
        b["subjects"] = subjects
        b["mediums"] = mediums
        b["levels"] = levels
        return b
    }

    override fun onResume() {
        super.onResume()
        selectAll.isChecked = false
        if (::recyclerView.isInitialized) {
            recyclerView.removeCallbacks(spanUpdateRunnable)
            recyclerView.post(spanUpdateRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        saveSearchActivity()
    }

    override fun onDestroyView() {
        if (::recyclerView.isInitialized) {
            recyclerView.removeOnLayoutChangeListener(layoutChangeListener)
            recyclerView.removeCallbacks(spanUpdateRunnable)
        }
        if (confirmation?.isShowing == true) {
            confirmation?.dismiss()
        }
        confirmation = null
        if (::adapterLibrary.isInitialized) {
            adapterLibrary.setListener(null)
        }

        layoutViewToggle = null
        toggleGridButton = null
        toggleListButton = null

        _binding = null
        super.onDestroyView()
    }

    private fun filterApplied(searchText: String): Boolean {
        return !(subjects.isEmpty() && languages.isEmpty()
                && mediums.isEmpty() && levels.isEmpty()
                && searchTags.isEmpty() && searchText.isEmpty())
    }

    private fun saveSearchActivity() {
        val searchText = etSearch.text?.toString().orEmpty()
        val userName = model?.name
        val planetCode = model?.planetCode
        val parentCode = model?.parentCode

        viewLifecycleOwner.lifecycleScope.launch {
            if (!filterApplied(searchText) || userName == null || planetCode == null || parentCode == null) {
                return@launch
            }

            viewModel.saveSearchActivity(
                userName,
                searchText,
                planetCode,
                parentCode,
                searchTags,
                subjects,
                languages,
                levels,
                mediums
            )
        }
    }

    private fun additionalSetup() {
        val bottomSheet = binding.cardFilter
        filter.setOnClickListener {
            bottomSheet.visibility = if (bottomSheet.isVisible) View.GONE else View.VISIBLE
        }
        binding.root.findViewById<View>(R.id.btn_close_filter)?.setOnClickListener {
            bottomSheet.visibility = View.GONE
        }
        binding.btnCollections.setOnClickListener {
            bottomSheet.visibility = View.GONE
        }
        binding.filterCategories.setOnClickListener {
            val f = ResourcesFilterFragment()
            f.setListener(this)
            f.show(childFragmentManager, "")
            bottomSheet.visibility = View.GONE
        }
        binding.orderByDateButton.setOnClickListener {
            bottomSheet.visibility = View.GONE
            viewLifecycleOwner.lifecycleScope.launch {
                val sorted = viewModel.toggleSortOrder(adapterLibrary.currentList)
                adapterLibrary.setLibraryList(sorted) {
                    recyclerView.scrollToPosition(0)
                }
            }
        }
        binding.orderByTitleButton.setOnClickListener {
            bottomSheet.visibility = View.GONE
            viewLifecycleOwner.lifecycleScope.launch {
                val sorted = viewModel.toggleTitleSortOrder(adapterLibrary.currentList)
                adapterLibrary.setLibraryList(sorted) {
                    recyclerView.scrollToPosition(0)
                }
            }
        }
    }
    
    override fun getWatchedTables(): List<String> {
        return listOf("resources")
    }
    
    override fun onDataUpdated(table: String, update: TableDataUpdate) {
        refreshResourcesData()
    }

    override fun shouldAutoRefresh(table: String): Boolean = false
    
    override fun getSyncRecyclerView(): RecyclerView? {
        return if (::recyclerView.isInitialized) recyclerView else null
    }

    private fun filterLocalLibraryByTag(models: List<ResourceListModel>, s: String, tags: List<TagEntity>): List<ResourceListModel> {
        var filteredList = ResourcesSearchUtils.searchLocalModels(models, s)

        if (tags.isNotEmpty()) {
            filteredList = filteredList.filter { model ->
                tags.any { searchTag -> model.tags.any { it.id == searchTag.id } }
            }
        }
        return filteredList
    }

    private fun applyFilterModels(models: List<ResourceListModel>): List<ResourceListModel> {
        val locallyOfflineIds = if (::adapterLibrary.isInitialized) adapterLibrary.getLocallyOfflineIds() else emptySet()
        return models.filter { model ->
            val l = model.library
            val sub = subjects.isEmpty() || subjects.let { l.subject?.containsAll(it) } == true
            val lev = levels.isEmpty() || l.level?.containsAll(levels) == true
            val lan = languages.isEmpty() || languages.contains(l.language)
            val med = mediums.isEmpty() || mediums.contains(l.mediaType)

            val isDownloaded = model.item.isOffline ||
                    locallyOfflineIds.contains(model.item.id) ||
                    model.isLocallyOffline
            val passesDownloadFilter = when (selectedDownloadFilterIndex) {
                1 -> isDownloaded
                2 -> !isDownloaded
                else -> true
            }

            sub && lev && lan && med && passesDownloadFilter
        }
    }

    override fun deleteSelected(deleteProgress: Boolean) {
        val userId = userModel?.id
        val itemsToDelete = selectedItems?.mapNotNull { it?.resourceId } ?: emptyList()

        if (userId != null && itemsToDelete.isNotEmpty()) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.removeResourcesFromShelf(itemsToDelete, userId)
                    .onSuccess {
                        _binding ?: return@onSuccess
                        Utilities.toast(activity, getString(R.string.removed_from_mylibrary))
                        refreshResourcesData()
                        selectedItems?.clear()
                        changeButtonStatus()
                        hideButton()
                    }
                    .onFailure {
                        _binding ?: return@onFailure
                        Utilities.toast(activity, getString(R.string.error, it.message))
                    }
            }
        }
    }

    override fun addToMyList(onComplete: (() -> Unit)?) {
        val userId = userModel?.id
        val itemsToAdd = selectedItems?.mapNotNull { it?.resourceId } ?: emptyList()

        if (userId != null && itemsToAdd.isNotEmpty()) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    viewModel.addResourcesToUserLibrary(itemsToAdd, userId)
                        .onSuccess {
                            _binding ?: return@onSuccess
                            Utilities.toast(activity, getString(R.string.added_to_my_library))
                            refreshResourcesData()
                            selectedItems?.clear()
                            changeButtonStatus()
                            hideButton()
                        }
                        .onFailure {
                            _binding ?: return@onFailure
                            Utilities.toast(activity, getString(R.string.error, it.message))
                        }
                } finally {
                    onComplete?.invoke()
                }
            }
        } else {
            onComplete?.invoke()
        }
    }

    private fun setupDownloadFilterChips() {
        val chipRow = binding.chipFilterRow
        chipRow.removeAllViews()
        val options = requireContext().resources.getStringArray(R.array.download_filter)
        options.indices.forEach { label ->
            val chip = layoutInflater.inflate(R.layout.item_filter_chip, chipRow, false) as TextView
            chip.text = options[label]
            chip.tag = label
            chip.setOnClickListener {
                selectedDownloadFilterIndex = label
                renderDownloadChipSelection(chipRow)
                applyFiltersAndUpdateUI()
            }
            chipRow.addView(chip)
        }
        renderDownloadChipSelection(chipRow)
    }

    private fun renderDownloadChipSelection(chipRow: LinearLayout) {
        val selected = selectedDownloadFilterIndex
        for (i in 0 until chipRow.childCount) {
            val chip = chipRow.getChildAt(i) as? TextView ?: continue
            val isSelected = (chip.tag as? Int) == selected
            chip.setBackgroundResource(if (isSelected) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
            chip.setTextColor(ContextCompat.getColor(requireContext(),
                if (isSelected) R.color.chip_selected_text else R.color.daynight_textColor))
        }
    }
}
