package org.ole.planet.myplanet.ui.resources

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import fisk.chipcloud.ChipCloud
import fisk.chipcloud.ChipCloudConfig
import fisk.chipcloud.ChipDeletedListener
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.callback.OnFilterListener
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnLibraryItemSelectedListener
import org.ole.planet.myplanet.callback.OnTagClickListener
import org.ole.planet.myplanet.databinding.FragmentMyLibraryBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.model.ResourceItem
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.model.TagItem
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.ui.sync.RealtimeSyncHelper
import org.ole.planet.myplanet.ui.sync.RealtimeSyncMixin
import org.ole.planet.myplanet.utils.DialogUtils
import org.ole.planet.myplanet.utils.DialogUtils.guestDialog
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.KeyboardUtils.setupUI
import org.ole.planet.myplanet.utils.collectWhenStarted
import org.ole.planet.myplanet.utils.Utilities

@AndroidEntryPoint
class ResourcesFragment : BaseRecyclerFragment<RealmMyLibrary?>(), OnLibraryItemSelectedListener,
    ChipDeletedListener, OnTagClickListener, OnFilterListener, RealtimeSyncMixin {
    private var _binding: FragmentMyLibraryBinding? = null
    private val binding get() = _binding!!
    private val tvAddToLib get() = binding.tvAdd
    private val tvSelected get() = binding.tvSelected
    private val etSearch get() = binding.layoutSearch.etSearch
    private val flexBoxTags get() = binding.layoutSearch.flexboxTags
    private val clearTags get() = binding.btnClearTags
    private val selectAll get() = binding.selectAll
    private val filter get() = binding.filter
    private lateinit var searchTags: MutableList<RealmTag>
    private lateinit var config: ChipCloudConfig
    private lateinit var adapterLibrary: ResourcesAdapter
    private var tagsMap: Map<String, List<RealmTag>> = emptyMap()
    var userModel: RealmUser ?= null
    var map: HashMap<String?, JsonObject>? = null
    private var confirmation: AlertDialog? = null
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null
    private var searchTextWatcher: TextWatcher? = null
    private var isFirstResume = true
    private var allLibraryItems: List<RealmMyLibrary> = emptyList()
    private var searchJob: Job? = null

    @Inject
    lateinit var prefManager: SharedPrefManager

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    private val viewModel: ResourcesViewModel by viewModels()
    
    private lateinit var realtimeSyncHelper: RealtimeSyncHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.startResourcesSync()

        collectWhenStarted(viewModel.syncState) { state ->
            when (state) {
                is org.ole.planet.myplanet.model.SyncState.Syncing -> {
                    if (isAdded && !requireActivity().isFinishing && view != null) {
                        customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
                        customProgressDialog?.setText(getString(R.string.syncing_resources))
                        customProgressDialog?.show()
                    }
                }
                is org.ole.planet.myplanet.model.SyncState.Success -> {
                    if (isAdded && view != null) {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null
                        refreshResourcesData()
                        viewModel.resetSyncState()
                    }
                }
                is org.ole.planet.myplanet.model.SyncState.Failed -> {
                    if (isAdded && view != null) {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null

                        Snackbar.make(requireView(), "Sync failed: ${state.message ?: "Unknown error"}", Snackbar.LENGTH_LONG
                        ).setAction("Retry") {
                            viewModel.startResourcesSync()
                        }.show()
                        viewModel.resetSyncState()
                    }
                }
                is org.ole.planet.myplanet.model.SyncState.Idle -> {
                    // Do nothing
                }
            }
        }
    }

    override fun getLayout(): Int {
        return R.layout.fragment_my_library
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        _binding = view?.let { FragmentMyLibraryBinding.bind(it) }
        return view
    }

    private suspend fun loadRatingsAndTags(allResourceIds: List<String>, userId: String?) {
        map = HashMap(resourcesRepository.getResourceRatingsBulk(allResourceIds, userId))
        tagsMap = resourcesRepository.getResourceTagsBulk(allResourceIds)
    }

    private fun refreshResourcesData() {
        if (!isAdded || requireActivity().isFinishing) return
        val binding = _binding ?: return
        val searchQuery = binding.layoutSearch.etSearch.text?.toString()?.trim().orEmpty()

        lifecycleScope.launch {
            try {
                allLibraryItems = if (isMyCourseLib) {
                    resourcesRepository.getMyLibrary(model?.id)
                } else {
                    resourcesRepository.getAllLibraryItems().filter {
                        !it.isPrivate && it.userId?.contains(model?.id) == false
                    }
                }

                val allResourceIds = allLibraryItems.mapNotNull { it.resourceId ?: it.id }

                loadRatingsAndTags(allResourceIds, model?.id)

                if (::adapterLibrary.isInitialized) {
                    adapterLibrary.setRatingMap(map ?: HashMap())
                    adapterLibrary.setTagsMap(tagsMap.mapValues { entry -> entry.value.map { it.toTagItem() } })
                }

                applyFiltersAndUpdateUI(scrollToTop = false)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun RealmMyLibrary.toResourceItem(): ResourceItem {
        return ResourceItem(
            id = id,
            title = title,
            description = description,
            createdDate = createdDate,
            averageRating = averageRating,
            timesRated = timesRated,
            resourceId = resourceId,
            isOffline = isResourceOffline(),
            _rev = _rev,
            uploadDate = uploadDate,
            filename = filename
        )
    }

    private fun RealmTag.toTagItem(): TagItem {
        return TagItem(id = id, name = name)
    }

    override suspend fun getAdapter(): RecyclerView.Adapter<out RecyclerView.ViewHolder> {
        allLibraryItems = if (isMyCourseLib) {
            resourcesRepository.getMyLibrary(model?.id)
        } else {
            resourcesRepository.getAllLibraryItems().filter {
                !it.isPrivate && it.userId?.contains(model?.id) == false
            }
        }

        val allResourceIds = allLibraryItems.mapNotNull { it.resourceId ?: it.id }

        loadRatingsAndTags(allResourceIds, model?.id)

        val user = profileDbHandler.getUserModel()
        adapterLibrary = ResourcesAdapter(requireActivity(), map ?: HashMap(), user?.isGuest() == true, emptyMap(), emptySet())

        val filteredList = filterLocalLibraryByTag(etSearch.text?.toString()?.trim().orEmpty(), searchTags)
        adapterLibrary.setLibraryList(filteredList.map { it.toResourceItem() })
        adapterLibrary.setTagsMap(tagsMap.mapValues { entry -> entry.value.map { it.toTagItem() } })

        adapterLibrary.setRatingChangeListener(this)
        adapterLibrary.setListener(this)

        checkList(filteredList.size)
        showNoData(tvMessage, filteredList.size, "resources")
        changeButtonStatus()
        return adapterLibrary
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isMyCourseLib = arguments?.getBoolean("isMyCourseLib", false) ?: false
        searchTags = ArrayList()
        config = Utilities.getCloudConfig().showClose(R.color.black_overlay)

        initializeViews()
        setupEventListeners()
        initArrays()
        hideButton()

        lifecycleScope.launch {
            userModel = profileDbHandler.getUserModel()
            setupGuestUserRestrictions()

            val userId = userModel?.id
            if (userId != null) {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    resourcesRepository.observeOpenedResourceIds(userId).collect { openedResourceIds ->
                        if (::adapterLibrary.isInitialized) {
                            adapterLibrary.setOpenedResourceIds(openedResourceIds)
                        }
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

        tvFragmentInfo = binding.tvFragmentInfo
        if (isMyCourseLib) tvFragmentInfo.setText(R.string.txt_myLibrary)
        
        realtimeSyncHelper = RealtimeSyncHelper(this, this)
        realtimeSyncHelper.setupRealtimeSync()
    }

    private fun initializeViews() {
        if (tvSelected.text.isNullOrEmpty()) {
            tvSelected.visibility = View.GONE
        } else {
            tvSelected.visibility = View.VISIBLE
        }
    }

    private fun setupGuestUserRestrictions() {
        if(userModel?.isGuest() == true){
            tvAddToLib.visibility = View.GONE
            selectAll.visibility = View.GONE
        }
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
                    viewLifecycleOwner.lifecycleScope.launch {
                        deleteSelected(true)
                    }
                }
                .setNegativeButton(R.string.no, null).show()
        }
    }

    private fun setupSearchTextListener() {
        searchTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                searchJob?.cancel()
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(300)
                    if (!::adapterLibrary.isInitialized || !isAdded || _binding == null) return@launch
                    applyFiltersAndUpdateUI()
                }
            }

            override fun afterTextChanged(s: Editable) {}
        }
        etSearch.addTextChangedListener(searchTextWatcher)
    }

    private suspend fun applyFiltersAndUpdateUI(scrollToTop: Boolean = true) {
        if (!::adapterLibrary.isInitialized || !isAdded || _binding == null) return
        val searchQuery = etSearch.text?.toString()?.trim().orEmpty()

        val currentSearchTags = if (::searchTags.isInitialized) searchTags else emptyList()
        val filteredList = applyFilter(filterLocalLibraryByTag(searchQuery, currentSearchTags))

        if (scrollToTop) {
            adapterLibrary.setLibraryList(filteredList.map { it.toResourceItem() }) {
                recyclerView.scrollToPosition(0)
            }
        } else {
            adapterLibrary.setLibraryList(filteredList.map { it.toResourceItem() })
        }

        checkList(filteredList.size)
        showNoData(tvMessage, adapterLibrary.itemCount, "resources")
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
        binding.addResource.setOnClickListener {
            if (userModel?.id?.startsWith("guest") == false) {
                AddResourceFragment().show(childFragmentManager, getString(R.string.add_res))
            } else {
                guestDialog(requireContext(), profileDbHandler)
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
        if(count != 0){
            if(isMyCourseLib) tvDelete?.visibility = View.VISIBLE
            else tvAddToLib.visibility = View.VISIBLE
        } else {
            if(isMyCourseLib) tvDelete?.visibility = View.GONE
            else tvAddToLib.visibility = View.GONE
        }
    }

    private fun checkList(listSize: Int = if (::adapterLibrary.isInitialized) adapterLibrary.getLibraryList().size else 0) {
        if (listSize == 0) {
            selectAll.visibility = View.GONE
            etSearch.visibility = View.GONE
            tvAddToLib.visibility = View.GONE
            tvSelected.visibility = View.GONE
            binding.btnCollections.visibility = View.GONE
            filter.visibility = View.GONE
            clearTags.visibility = View.GONE
            tvDelete?.visibility = View.GONE
        } else {
            selectAll.visibility = View.VISIBLE
            etSearch.visibility = View.VISIBLE
            binding.btnCollections.visibility = View.VISIBLE
            filter.visibility = View.VISIBLE
        }
    }

    private fun initArrays() {
        subjects = HashSet()
        languages = HashSet()
        levels = HashSet()
        mediums = HashSet()
    }

    private fun createAlertDialog(): AlertDialog {
        val builder = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
        builder.setMessage(buildAlertMessage())
        builder.setCancelable(true)
            .setPositiveButton(R.string.go_to_mylibrary) { dialog: DialogInterface, _: Int ->
                if (userModel?.id?.startsWith("guest") == true) {
                    guestDialog(requireContext(), profileDbHandler)
                } else {
                    val fragment = ResourcesFragment().apply {
                        arguments = Bundle().apply {
                            putBoolean("isMyCourseLib", true)
                        }
                    }
                    homeItemClickListener?.openMyFragment(fragment)
                }
            }
        builder.setNegativeButton(getString(R.string.ok)) { dialog: DialogInterface, _: Int ->
            dialog.cancel()
        }
        builder.setOnDismissListener {
            addToMyList()
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
            saveSearchActivity()
            searchTags.clear()
            etSearch.setText(R.string.empty_text)
            tvSelected.text = getString(R.string.empty_text)
            levels.clear()
            mediums.clear()
            subjects.clear()
            languages.clear()
            viewLifecycleOwner.lifecycleScope.launch {
                applyFiltersAndUpdateUI()
            }
        }
    }

    override fun onSelectedListChange(list: List<ResourceItem>) {
        val newSelected = list.mapNotNull { item ->
            allLibraryItems.find { it.id == item.id }
        }
        selectedItems?.clear()
        selectedItems?.addAll(newSelected)
        changeButtonStatus()
        hideButton()
    }

    override fun onTagClicked(tag: TagItem) {
        val realmTag = tagsMap.values.flatten().find { it.id == tag.id }
        if (realmTag != null) {
            onTagClicked(realmTag)
        }
    }

    override fun onResourceClicked(item: ResourceItem) {
        val library = allLibraryItems.find { it.id == item.id }
        if (library != null) {
            homeItemClickListener?.openLibraryDetailFragment(library)
        }
    }

    override fun onTagClicked(tag: RealmTag) {
        tvSelected.visibility = View.VISIBLE
        flexBoxTags.removeAllViews()
        val chipCloud = ChipCloud(activity, flexBoxTags, config)
        chipCloud.setDeleteListener(this)
        if (!searchTags.any { it.name == tag.name }) searchTags.add(tag)
        chipCloud.addChips(searchTags)
        showTagText(searchTags, tvSelected)
        viewLifecycleOwner.lifecycleScope.launch {
            applyFiltersAndUpdateUI()
        }
    }

    override fun onTagSelected(tag: RealmTag) {
        tvSelected.visibility = View.VISIBLE
        val li: MutableList<RealmTag> = ArrayList()
        li.add(tag)
        searchTags = li
        tvSelected.text = getString(R.string.tag_selected, tag.name)
        viewLifecycleOwner.lifecycleScope.launch {
            applyFiltersAndUpdateUI()
        }
    }

    override fun onOkClicked(list: List<RealmTag>?) {
        if (list?.isEmpty() == true) {
            searchTags.clear()
            viewLifecycleOwner.lifecycleScope.launch {
                applyFiltersAndUpdateUI()
            }
        } else {
            for (tag in list ?: emptyList()) {
                onTagClicked(tag)
            }
        }
    }

    private fun changeButtonStatus() {
        tvAddToLib.isEnabled = (selectedItems?.size ?: 0) > 0
        if (adapterLibrary.areAllSelected()) {
            selectAll.isChecked = true
            selectAll.text = getString(R.string.unselect_all)
        } else {
            selectAll.isChecked = false
            selectAll.text = getString(R.string.select_all)
        }
    }

    override fun chipDeleted(i: Int, s: String) {
        searchTags.removeAt(i)
        viewLifecycleOwner.lifecycleScope.launch {
            applyFiltersAndUpdateUI()
        }
    }

    override fun filter(subjects: MutableSet<String>, languages: MutableSet<String>, mediums: MutableSet<String>, levels: MutableSet<String>) {
        this.subjects = subjects
        this.languages = languages
        this.mediums = mediums
        this.levels = levels
        viewLifecycleOwner.lifecycleScope.launch {
            applyFiltersAndUpdateUI()
        }
    }

    override suspend fun getData(): Map<String, Set<String>> {
        val currentIds = adapterLibrary.getLibraryList().mapNotNull { it.id }.toSet()
        val libraryList = allLibraryItems.filter { it.id in currentIds }
        return resourcesRepository.getFilterFacets(libraryList)
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
        if (isFirstResume) {
            refreshResourcesData()
            isFirstResume = false
        }
        selectAll.isChecked = false
    }

    override fun onPause() {
        super.onPause()
        saveSearchActivity()
    }

    override fun onDestroyView() {
        isFirstResume = true
        etSearch.removeTextChangedListener(searchTextWatcher)
        searchTextWatcher = null

        if (confirmation?.isShowing == true) {
            confirmation?.dismiss()
        }
        confirmation = null

        if (customProgressDialog?.isShowing() == true) {
            customProgressDialog?.dismiss()
        }
        customProgressDialog = null

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

        lifecycleScope.launch(dispatcherProvider.io) {
            if (!filterApplied(searchText) || userName == null || planetCode == null || parentCode == null) {
                return@launch
            }

            resourcesRepository.saveSearchActivity(
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

    override fun onDestroy() {
        customProgressDialog?.dismiss()
        customProgressDialog = null
        super.onDestroy()
    }

    private fun additionalSetup() {
        val bottomSheet = binding.cardFilter
        filter.setOnClickListener {
            bottomSheet.visibility = if (bottomSheet.isVisible) View.GONE else View.VISIBLE
        }
        binding.filterCategories.setOnClickListener {
            val f = ResourcesFilterFragment()
            f.setListener(this)
            f.show(childFragmentManager, "")
            bottomSheet.visibility = View.GONE
        }
        binding.orderByDateButton.setOnClickListener {
            adapterLibrary.toggleSortOrder {
                recyclerView.scrollToPosition(0)
            }
        }
        binding.orderByTitleButton.setOnClickListener {
            adapterLibrary.toggleTitleSortOrder {
                recyclerView.scrollToPosition(0)
            }
        }
    }
    
    override fun getWatchedTables(): List<String> {
        return listOf("resources")
    }
    
    override fun onDataUpdated(table: String, update: TableDataUpdate) {
        refreshResourcesData()
    }

    override fun shouldAutoRefresh(table: String): Boolean = true
    
    override fun getSyncRecyclerView(): RecyclerView? {
        return if (::recyclerView.isInitialized) recyclerView else null
    }

    private suspend fun filterLocalLibraryByTag(s: String, tags: List<RealmTag>): List<RealmMyLibrary> {
        var filteredList = resourcesRepository.search(s, isMyCourseLib, model?.id)

        if (tags.isNotEmpty()) {
            filteredList = filteredList.filter { library ->
                val libraryTags = library.id?.let { tagsMap[it] } ?: emptyList()
                tags.any { searchTag -> libraryTags.any { it.id == searchTag.id } }
            }
        }
        return filteredList
    }

    override suspend fun deleteSelected(deleteProgress: Boolean) {
        val userId = userModel?.id
        val itemsToDelete = selectedItems?.mapNotNull { it?.resourceId } ?: emptyList()

        if (userId != null && itemsToDelete.isNotEmpty()) {
            lifecycleScope.launch(dispatcherProvider.io) {
                itemsToDelete.forEach { resourceId ->
                    resourcesRepository.removeResourceFromShelf(resourceId, userId)
                }
                withContext(dispatcherProvider.main) {
                    _binding ?: return@withContext
                    Utilities.toast(activity, getString(R.string.removed_from_mylibrary))
                    refreshResourcesData()
                    selectedItems?.clear()
                    changeButtonStatus()
                    hideButton()
                }
            }
        }
    }

    override fun addToMyList() {
        val userId = userModel?.id
        val itemsToAdd = selectedItems?.mapNotNull { it?.resourceId } ?: emptyList()

        if (userId != null && itemsToAdd.isNotEmpty()) {
            lifecycleScope.launch {
                resourcesRepository.addResourcesToUserLibrary(itemsToAdd, userId)
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
            }
        }
    }
}
