package org.ole.planet.myplanet.ui.resources

import android.app.AlertDialog
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import fisk.chipcloud.ChipCloud
import fisk.chipcloud.ChipCloudConfig
import fisk.chipcloud.ChipDeletedListener
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.callback.OnFilterListener
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnLibraryItemSelected
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.callback.TableDataUpdate
import org.ole.planet.myplanet.callback.TagClickListener
import org.ole.planet.myplanet.databinding.FragmentMyLibraryBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.getArrayList
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.getLevels
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.getSubjects
import org.ole.planet.myplanet.model.RealmRating.Companion.getRatings
import org.ole.planet.myplanet.model.RealmSearchActivity
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmTag.Companion.getTagsArray
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.TagRepository
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.ui.sync.RealtimeSyncHelper
import org.ole.planet.myplanet.ui.sync.RealtimeSyncMixin
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.DialogUtils.guestDialog
import org.ole.planet.myplanet.utilities.KeyboardUtils.setupUI
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class ResourcesFragment : BaseRecyclerFragment<RealmMyLibrary?>(), OnLibraryItemSelected,
    ChipDeletedListener, TagClickListener, OnFilterListener, RealtimeSyncMixin {
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
    private lateinit var adapterLibrary: AdapterResource
    var userModel: RealmUserModel ?= null
    var map: HashMap<String?, JsonObject>? = null
    private var confirmation: AlertDialog? = null
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null
    private var searchTextWatcher: TextWatcher? = null

    @Inject
    lateinit var prefManager: SharedPrefManager

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var tagRepository: TagRepository

    @Inject
    lateinit var serverUrlMapper: ServerUrlMapper
    private val serverUrl: String
        get() = settings.getString("serverURL", "") ?: ""
    
    private lateinit var realtimeSyncHelper: RealtimeSyncHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = requireActivity().getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        startResourcesSync()
    }

    override fun getLayout(): Int {
        return R.layout.fragment_my_library
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        _binding = view?.let { FragmentMyLibraryBinding.bind(it) }
        return view
    }

    private fun startResourcesSync() {
        val isFastSync = settings.getBoolean("fastSync", false)
        if (isFastSync && !prefManager.isResourcesSynced()) {
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
                lifecycleScope.launch {
                    if (isAdded && !requireActivity().isFinishing && view != null) {
                        customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
                        customProgressDialog?.setText(getString(R.string.syncing_resources))
                        customProgressDialog?.show()
                    }
                }
            }

            override fun onSyncComplete() {
                lifecycleScope.launch {
                    if (isAdded && view != null) {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null
                        refreshResourcesData()
                        prefManager.setResourcesSynced(true)
                    }
                }
            }

            override fun onSyncFailed(msg: String?) {
                lifecycleScope.launch {
                    if (isAdded && view != null) {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null

                        Snackbar.make(requireView(), "Sync failed: ${msg ?: "Unknown error"}", Snackbar.LENGTH_LONG
                        ).setAction("Retry") {
                            startResourcesSync()
                        }.show()
                    }
                }
            }
        }, "full", listOf("resources"))
    }

    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping, settings) { url ->
            isServerReachable(url)
        }
    }

    private fun refreshResourcesData() {
        if (!isAdded || requireActivity().isFinishing) return

        try {
            map = getRatings(mRealm, "resource", model?.id)
            val libraryList: List<RealmMyLibrary?> = getList(RealmMyLibrary::class.java).filterIsInstance<RealmMyLibrary?>()
            val currentSearchTags = if (::searchTags.isInitialized) searchTags else emptyList()
            val searchQuery = etSearch.text?.toString()?.trim().orEmpty()
            val filteredLibraryList: List<RealmMyLibrary?> =
                if (currentSearchTags.isEmpty() && searchQuery.isEmpty()) {
                    applyFilter(libraryList.filterNotNull()).map { it }
                } else {
                    applyFilter(filterLibraryByTag(searchQuery, currentSearchTags)).map { it }
                }

            adapterLibrary.setLibraryList(filteredLibraryList)
            adapterLibrary.setRatingMap(map!!)
            checkList()
            showNoData(tvMessage, adapterLibrary.itemCount, "resources")

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getAdapter(): RecyclerView.Adapter<*> {
        map = getRatings(mRealm, "resource", model?.id)
        val libraryList: List<RealmMyLibrary?> = getList(RealmMyLibrary::class.java).filterIsInstance<RealmMyLibrary?>()
        adapterLibrary = AdapterResource(requireActivity(), libraryList, map!!, tagRepository, profileDbHandler?.userModel, databaseService)
        adapterLibrary.setRatingChangeListener(this)
        adapterLibrary.setListener(this)
        return adapterLibrary
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isMyCourseLib = arguments?.getBoolean("isMyCourseLib", false) ?: false
        userModel = profileDbHandler?.userModel
        searchTags = ArrayList()
        config = Utilities.getCloudConfig().showClose(R.color.black_overlay)

        initializeViews()
        setupEventListeners()
        initArrays()
        hideButton()

        setupGuestUserRestrictions()

        showNoData(tvMessage, adapterLibrary.itemCount, "resources")
        clearTagsButton()
        setupUI(binding.myLibraryParentLayout, requireActivity())
        changeButtonStatus()
        additionalSetup()

        tvFragmentInfo = binding.tvFragmentInfo
        if (isMyCourseLib) tvFragmentInfo.setText(R.string.txt_myLibrary)
        checkList()
        
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
                addToMyList()
                selectedItems?.clear()
                tvAddToLib.isEnabled = false
                checkList()
            }
        }
    }

    private fun setupDeleteListener() {
        tvDelete?.setOnClickListener {
            AlertDialog.Builder(this.context, R.style.AlertDialogTheme)
                .setMessage(R.string.confirm_removal)
                .setPositiveButton(R.string.yes) { _, _ ->
                    deleteSelected(true)
                    val newFragment = ResourcesFragment()
                    recreateFragment(newFragment)
                }
                .setNegativeButton(R.string.no, null).show()
        }
    }

    private fun setupSearchTextListener() {
        searchTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                adapterLibrary.setLibraryList(
                    applyFilter(
                        filterLibraryByTag(
                            etSearch.text.toString().trim(), searchTags
                        )
                    )
                )
                showNoData(tvMessage, adapterLibrary.itemCount, "resources")
            }

            override fun afterTextChanged(s: Editable) {}
        }
        etSearch.addTextChangedListener(searchTextWatcher)
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
            val allSelected = selectedItems?.size == adapterLibrary.getLibraryList().size
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
        tvDelete?.isEnabled = selectedItems?.size!! != 0
        tvAddToLib.isEnabled = selectedItems?.size!! != 0
        if(selectedItems?.size!! != 0){
            if(isMyCourseLib) tvDelete?.visibility = View.VISIBLE
            else tvAddToLib.visibility = View.VISIBLE
        } else {
            if(isMyCourseLib) tvDelete?.visibility = View.GONE
            else tvAddToLib.visibility = View.GONE
        }
    }

    private fun checkList() {
        if (adapterLibrary.getLibraryList().isEmpty()) {
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
            val newFragment = ResourcesFragment()
            recreateFragment(newFragment)
        }
        builder.setOnDismissListener{
            val newFragment = ResourcesFragment()
            recreateFragment(newFragment)
        }
        return builder.create()
    }

    private fun buildAlertMessage(): String {
        var msg = getString(R.string.success_you_have_added_these_resources_to_your_mylibrary)
        if ((selectedItems?.size ?: 0) <= 5) {
            for (i in selectedItems?.indices ?: emptyList()) {
                msg += " - " + selectedItems!![i]?.title + "\n"
            }
        } else {
            for (i in 0..4) {
                msg += " - " + selectedItems?.get(i)?.title + "\n"
            }
            msg += getString(R.string.and) + ((selectedItems?.size ?: 0) - 5) +
                getString(R.string.more_resource_s)
        }
        msg += getString(R.string.return_to_the_home_tab_to_access_mylibrary) +
            getString(R.string.note_you_may_still_need_to_download_the_newly_added_resources)
        return msg
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
            adapterLibrary.setLibraryList(applyFilter(filterLibraryByTag("", searchTags)))
            showNoData(tvMessage, adapterLibrary.itemCount, "resources")
        }
    }

    override fun onSelectedListChange(list: MutableList<RealmMyLibrary?>) {
        selectedItems = list
        changeButtonStatus()
        hideButton()
    }

    override fun onTagClicked(realmTag: RealmTag) {
        tvSelected.visibility = View.VISIBLE
        flexBoxTags.removeAllViews()
        val chipCloud = ChipCloud(activity, flexBoxTags, config)
        chipCloud.setDeleteListener(this)
        if (!searchTags.contains(realmTag)) searchTags.add(realmTag)
        chipCloud.addChips(searchTags)
        adapterLibrary.setLibraryList(applyFilter(filterLibraryByTag(etSearch.text.toString(), searchTags)))
        showTagText(searchTags, tvSelected)
        showNoData(tvMessage, adapterLibrary.itemCount, "resources")
    }

    override fun onTagSelected(tag: RealmTag) {
        tvSelected.visibility = View.VISIBLE
        val li: MutableList<RealmTag> = ArrayList()
        li.add(tag)
        searchTags = li
        tvSelected.text = getString(R.string.tag_selected, tag.name)
        adapterLibrary.setLibraryList(applyFilter(filterLibraryByTag(etSearch.text.toString(), li)))
        showNoData(tvMessage, adapterLibrary.itemCount, "resources")
    }

    override fun onOkClicked(list: List<RealmTag>?) {
        if (list?.isEmpty() == true) {
            searchTags.clear()
            adapterLibrary.setLibraryList(applyFilter(filterLibraryByTag(etSearch.text.toString(), searchTags)))
            showNoData(tvMessage, adapterLibrary.itemCount, "resources")
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
        adapterLibrary.setLibraryList(applyFilter(filterLibraryByTag(etSearch.text.toString(), searchTags)))
        showNoData(tvMessage, adapterLibrary.itemCount, "resources")
    }

    override fun filter(subjects: MutableSet<String>, languages: MutableSet<String>, mediums: MutableSet<String>, levels: MutableSet<String>) {
        this.subjects = subjects
        this.languages = languages
        this.mediums = mediums
        this.levels = levels
        adapterLibrary.setLibraryList(applyFilter(filterLibraryByTag(etSearch.text.toString().trim { it <= ' ' }, searchTags)))
        showNoData(tvMessage, adapterLibrary.itemCount, "resources")
    }

    override fun getData(): Map<String, Set<String>> {
        val libraryList = adapterLibrary.getLibraryList().filterNotNull()
        val b: MutableMap<String, Set<String>> = HashMap()
        b["languages"] = libraryList.let { getArrayList(it, "languages").filterNotNull().toSet() }
        b["subjects"] = libraryList.let { getSubjects(it).toList().toSet() }
        b["mediums"] = getArrayList(libraryList, "mediums").filterNotNull().toSet()
        b["levels"] = getLevels(libraryList).toList().toSet()
        return b
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
        refreshResourcesData()
        selectAll.isChecked = false
    }

    override fun onPause() {
        super.onPause()
        saveSearchActivity()
    }

    override fun onDestroyView() {
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

        if (::realtimeSyncHelper.isInitialized) {
            realtimeSyncHelper.cleanup()
        }

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

        lifecycleScope.launch(Dispatchers.IO) {
            if (!filterApplied(searchText) || userName == null || planetCode == null || parentCode == null) {
                return@launch
            }

            val filter = JsonObject().apply {
                add("tags", getTagsArray(searchTags))
                add("subjects", getJsonArrayFromList(subjects))
                add("language", getJsonArrayFromList(languages))
                add("level", getJsonArrayFromList(levels))
                add("mediaType", getJsonArrayFromList(mediums))
            }
            val filterPayload = Gson().toJson(filter)
            val createdAt = Calendar.getInstance().timeInMillis
            val activityId = UUID.randomUUID().toString()

            databaseService.executeTransactionAsync { realm ->
                val activity = realm.createObject(RealmSearchActivity::class.java, activityId)
                activity.user = userName
                activity.time = createdAt
                activity.createdOn = planetCode
                activity.parentCode = parentCode
                activity.text = searchText
                activity.type = "resources"
                activity.filter = filterPayload
            }
        }
    }

    override fun onDestroy() {
        customProgressDialog?.dismiss()
        customProgressDialog = null
        super.onDestroy()
    }

    private fun recreateFragment(fragment: Fragment) {
        if (isAdded && activity != null && !requireActivity().isFinishing) {
            if (isMyCourseLib) {
                val args = Bundle().apply {
                    putBoolean("isMyCourseLib", true)
                }
                fragment.arguments = args
            }
            NavigationHelper.replaceFragment(
                parentFragmentManager,
                R.id.fragment_container,
                fragment,
                addToBackStack = true
            )
        }
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
        binding.orderByDateButton.setOnClickListener { adapterLibrary.toggleSortOrder() }
        binding.orderByTitleButton.setOnClickListener { adapterLibrary.toggleTitleSortOrder() }
    }
    
    override fun getWatchedTables(): List<String> {
        return listOf("resources")
    }
    
    override fun onDataUpdated(table: String, update: TableDataUpdate) {}

    override fun shouldAutoRefresh(table: String): Boolean = true
    
    override fun getSyncRecyclerView(): RecyclerView? {
        return if (::recyclerView.isInitialized) recyclerView else null
    }
    
}
