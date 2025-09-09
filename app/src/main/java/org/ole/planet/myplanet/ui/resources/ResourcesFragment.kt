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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import fisk.chipcloud.ChipCloud
import fisk.chipcloud.ChipCloudConfig
import fisk.chipcloud.ChipDeletedListener
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.callback.OnFilterListener
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnLibraryItemSelected
import org.ole.planet.myplanet.callback.TableDataUpdate
import org.ole.planet.myplanet.callback.TagClickListener
import org.ole.planet.myplanet.databinding.FragmentMyLibraryBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.getArrayList
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.getLevels
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.getSubjects
import org.ole.planet.myplanet.model.RealmSearchActivity
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmTag.Companion.getTagsArray
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.ui.sync.RealtimeSyncHelper
import org.ole.planet.myplanet.ui.sync.RealtimeSyncMixin
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.DialogUtils.guestDialog
import org.ole.planet.myplanet.utilities.KeyboardUtils.setupUI
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
    private lateinit var config: ChipCloudConfig
    private lateinit var adapterLibrary: AdapterResource
    var userModel: RealmUserModel ?= null
    var map: HashMap<String?, JsonObject>? = null
    private var confirmation: AlertDialog? = null
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null
    private var searchTextWatcher: TextWatcher? = null

    private lateinit var viewModel: ResourcesViewModel

    private lateinit var realtimeSyncHelper: RealtimeSyncHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = requireActivity().getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        viewModel = ViewModelProvider(
            requireActivity().viewModelStore,
            defaultViewModelProviderFactory,
            requireActivity().defaultViewModelCreationExtras
        )[ResourcesViewModel::class.java]
        viewModel.startResourcesSync()
    }

    override fun getLayout(): Int {
        return R.layout.fragment_my_library
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        _binding = view?.let { FragmentMyLibraryBinding.bind(it) }
        return view
    }

    override fun getAdapter(): RecyclerView.Adapter<*> {
        map = HashMap()
        adapterLibrary = AdapterResource(requireActivity(), emptyList(), map!!, mRealm)
        adapterLibrary.setRatingChangeListener(this)
        adapterLibrary.setListener(this)
        return adapterLibrary
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isMyCourseLib = arguments?.getBoolean("isMyCourseLib", false) ?: false
        userModel = profileDbHandler.userModel
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

        viewModel.initialize(model?.id, isMyCourseLib)
        collectViewModelFlows()
    }

    private fun collectViewModelFlows() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    viewModel.resourceList.collect { list ->
                        adapterLibrary.setLibraryList(list)
                        showNoData(tvMessage, adapterLibrary.itemCount, "resources")
                        checkList()
                    }
                }
                launch {
                    viewModel.ratingMap.collect { map ->
                        adapterLibrary.setRatingMap(map)
                        adapterLibrary.notifyDataSetChanged()
                    }
                }
                launch {
                    viewModel.tags.collect { tags ->
                        updateTagViews(tags)
                    }
                }
                launch {
                    viewModel.syncState.collect { state ->
                        when (state) {
                            is ResourcesViewModel.SyncState.Syncing -> {
                                if (customProgressDialog == null) {
                                    customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
                                    customProgressDialog?.setText(getString(R.string.syncing_resources))
                                    customProgressDialog?.show()
                                }
                            }
                            is ResourcesViewModel.SyncState.Success -> {
                                customProgressDialog?.dismiss()
                                customProgressDialog = null
                            }
                            is ResourcesViewModel.SyncState.Error -> {
                                customProgressDialog?.dismiss()
                                customProgressDialog = null
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    private fun updateTagViews(tags: List<RealmTag>) {
        tvSelected.visibility = if (tags.isEmpty()) View.GONE else View.VISIBLE
        flexBoxTags.removeAllViews()
        if (tags.isNotEmpty()) {
            val chipCloud = ChipCloud(activity, flexBoxTags, config)
            chipCloud.setDeleteListener(this)
            chipCloud.addChips(tags)
        }
        showTagText(tags, tvSelected)
        showNoData(tvMessage, adapterLibrary.itemCount, "resources")
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
                viewModel.updateSearchQuery(s.toString().trim())
            }
            override fun afterTextChanged(s: Editable) {}
        }
        etSearch.addTextChangedListener(searchTextWatcher)
    }

    private fun setupCollectionsButton() {
        binding.btnCollections.setOnClickListener {
            val f = CollectionsFragment.getInstance(viewModel.tags.value.toMutableList(), "resources")
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
            selectAll.isChecked = false
            selectAll.text = getString(R.string.select_all)
            etSearch.visibility = View.VISIBLE
            filter.visibility = View.VISIBLE
            if (userModel?.isGuest() == true) {
                selectAll.visibility = View.GONE
                tvAddToLib.visibility = View.GONE
            }
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
                    guestDialog(requireContext())
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
            etSearch.setText(R.string.empty_text)
            tvSelected.text = getString(R.string.empty_text)
            levels.clear()
            mediums.clear()
            subjects.clear()
            languages.clear()
            viewModel.clearFilters()
            viewModel.clearTags()
        }
    }

    override fun onSelectedListChange(list: MutableList<RealmMyLibrary?>) {
        selectedItems = list
        changeButtonStatus()
        hideButton()
    }

    override fun onTagClicked(realmTag: RealmTag) {
        viewModel.addTag(realmTag)
    }

    override fun onTagSelected(tag: RealmTag) {
        viewModel.setTags(listOf(tag))
    }

    override fun onRatingChanged() {
        viewModel.refreshRatings()
    }

    override fun onOkClicked(list: List<RealmTag>?) {
        viewModel.setTags(list ?: emptyList())
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
        viewModel.removeTagAt(i)
    }

    override fun filter(subjects: MutableSet<String>, languages: MutableSet<String>, mediums: MutableSet<String>, levels: MutableSet<String>) {
        this.subjects = subjects
        this.languages = languages
        this.mediums = mediums
        this.levels = levels
        viewModel.setFilters(subjects, languages, mediums, levels)
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
        viewModel.clearSearch()
        etSearch.setText("")
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

    private fun filterApplied(): Boolean {
        return !(subjects.isEmpty() && languages.isEmpty()
                && mediums.isEmpty() && levels.isEmpty()
                && viewModel.tags.value.isEmpty() && "${etSearch.text}".isEmpty())
    }

    private fun saveSearchActivity() {
        if (filterApplied()) {
            if (!mRealm.isInTransaction) mRealm.beginTransaction()
            val activity = mRealm.createObject(RealmSearchActivity::class.java, UUID.randomUUID().toString())
            activity.user = model?.name!!
            activity.time = Calendar.getInstance().timeInMillis
            activity.createdOn = model?.planetCode!!
            activity.parentCode = model?.parentCode!!
            activity.text = "${etSearch.text}"
            activity.type = "resources"
            val filter = JsonObject()
            filter.add("tags", getTagsArray(viewModel.tags.value))
            filter.add("subjects", getJsonArrayFromList(subjects))
            filter.add("language", getJsonArrayFromList(languages))
            filter.add("level", getJsonArrayFromList(levels))
            filter.add("mediaType", getJsonArrayFromList(mediums))
            activity.filter = Gson().toJson(filter)
            mRealm.commitTransaction()
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
    
    override fun onDataUpdated(table: String, update: TableDataUpdate) {
        if (table == "resources" && update.shouldRefreshUI) {
            viewModel.refreshResources()
        }
    }
    
    override fun getSyncRecyclerView(): RecyclerView? {
        return if (::recyclerView.isInitialized) recyclerView else null
    }
    
}
