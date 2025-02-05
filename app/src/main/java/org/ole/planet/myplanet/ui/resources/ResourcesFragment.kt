package org.ole.planet.myplanet.ui.resources

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.github.clans.fab.FloatingActionButton
import com.google.android.flexbox.FlexboxLayout
import com.google.gson.Gson
import com.google.gson.JsonObject
import fisk.chipcloud.ChipCloud
import fisk.chipcloud.ChipCloudConfig
import fisk.chipcloud.ChipDeletedListener
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.callback.OnFilterListener
import org.ole.planet.myplanet.callback.OnLibraryItemSelected
import org.ole.planet.myplanet.callback.TagClickListener
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.getArrayList
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.getLevels
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.getSubjects
import org.ole.planet.myplanet.model.RealmRating.Companion.getRatings
import org.ole.planet.myplanet.model.RealmSearchActivity
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmTag.Companion.getTagsArray
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import android.content.Context
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.utilities.DialogUtils.guestDialog
import org.ole.planet.myplanet.utilities.KeyboardUtils.setupUI
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Calendar
import java.util.UUID

class ResourcesFragment : BaseRecyclerFragment<RealmMyLibrary?>(), OnLibraryItemSelected,
    ChipDeletedListener, TagClickListener, OnFilterListener {
    private lateinit var tvAddToLib: TextView
    private lateinit var tvSelected: TextView
    private lateinit var etSearch: EditText
    private lateinit var etTags: EditText
    private lateinit var flexBoxTags: FlexboxLayout
    private lateinit var searchTags: MutableList<RealmTag>
    private lateinit var config: ChipCloudConfig
    private lateinit var clearTags: Button
    private lateinit var orderByTitle: Button
    private lateinit var orderByDate: Button
    private lateinit var selectAll: CheckBox
    private lateinit var filter: ImageButton
    private lateinit var adapterLibrary: AdapterResource
    private lateinit var addResourceButton: FloatingActionButton
    var userModel: RealmUserModel ?= null
    var map: HashMap<String?, JsonObject>? = null
    private var confirmation: AlertDialog? = null

    override fun getLayout(): Int {
        return R.layout.fragment_my_library
    }

    override fun getAdapter(): RecyclerView.Adapter<*> {
        map = getRatings(mRealm, "resource", model?.id)
        val libraryList: List<RealmMyLibrary?> = getList(RealmMyLibrary::class.java).filterIsInstance<RealmMyLibrary?>()
        adapterLibrary = AdapterResource(requireActivity(), libraryList, map!!, mRealm)
        adapterLibrary.setRatingChangeListener(this)
        adapterLibrary.setListener(this)
        return adapterLibrary
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isMyCourseLib = arguments?.getBoolean("isMyCourseLib", false) ?: false
        userModel = UserProfileDbHandler(requireContext()).userModel
        searchTags = ArrayList()
        config = Utilities.getCloudConfig().showClose(R.color.black_overlay)
        tvAddToLib = view.findViewById(R.id.tv_add)
        etSearch = view.findViewById(R.id.et_search)
        etTags = view.findViewById(R.id.et_tags)
        clearTags = view.findViewById(R.id.btn_clear_tags)
        tvSelected = view.findViewById(R.id.tv_selected)
        flexBoxTags = view.findViewById(R.id.flexbox_tags)
        selectAll = view.findViewById(R.id.selectAll)
        filter = view.findViewById(R.id.filter)
        addResourceButton = view.findViewById(R.id.addResource)
        if (tvSelected.text.isNullOrEmpty()) {
            tvSelected.visibility = View.GONE
        } else {
            tvSelected.visibility = View.VISIBLE
        }
        initArrays()
        updateTvDelete()

        if(userModel?.isGuest() == true){
            tvAddToLib.visibility = View.GONE
            selectAll.visibility = View.GONE
        }

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

        etSearch.addTextChangedListener(object : TextWatcher {
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
        })

        view.findViewById<View>(R.id.btn_collections).setOnClickListener {
            val f = CollectionsFragment.getInstance(searchTags, "resources")
            f.setListener(this@ResourcesFragment)
            f.show(childFragmentManager, "")
        }
        showNoData(tvMessage, adapterLibrary.itemCount, "resources")
        clearTagsButton()
        setupUI(view.findViewById(R.id.my_library_parent_layout), requireActivity())
        changeButtonStatus()
        additionalSetup()
        tvFragmentInfo = view.findViewById(R.id.tv_fragment_info)
        if (isMyCourseLib) tvFragmentInfo.setText(R.string.txt_myLibrary)
        checkList()

        selectAll.setOnClickListener {
            updateTvDelete()
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

        addResourceButton.setOnClickListener {
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
    private fun updateTvDelete(){
        tvDelete?.isEnabled = selectedItems?.size!! != 0
    }

    private fun checkList() {
        if (adapterLibrary.getLibraryList().isEmpty()) {
            selectAll.visibility = View.GONE
            etSearch.visibility = View.GONE
            tvAddToLib.visibility = View.GONE
            tvSelected.visibility = View.GONE
            requireView().findViewById<View>(R.id.btn_collections).visibility = View.GONE
            requireView().findViewById<View>(R.id.filter).visibility = View.GONE
            clearTags.visibility = View.GONE
            tvDelete?.visibility = View.GONE
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
        var msg = getString(R.string.success_you_have_added_these_resources_to_your_mylibrary)
        if ((selectedItems?.size ?: 0) <= 5) {
            for (i in selectedItems?.indices ?: emptyList()) {
                msg += " - " + selectedItems!![i]?.title + "\n"
            }
        } else {
            for (i in 0..4) {
                msg += " - " + selectedItems?.get(i)?.title + "\n"
            }
            msg += getString(R.string.and) + ((selectedItems?.size ?: 0) - 5) + getString(R.string.more_resource_s)
        }
        msg += getString(R.string.return_to_the_home_tab_to_access_mylibrary) + getString(R.string.note_you_may_still_need_to_download_the_newly_added_resources)
        builder.setMessage(msg)
        builder.setCancelable(true)
        .setPositiveButton("Go To MyLibrary") { dialog: DialogInterface, _: Int ->
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
        updateTvDelete()
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

    override fun onPause() {
        super.onPause()
        saveSearchActivity()
    }

    private fun filterApplied(): Boolean {
        return !(subjects.isEmpty() && languages.isEmpty()
                && mediums.isEmpty() && levels.isEmpty()
                && searchTags.isEmpty() && "${etSearch.text}".isEmpty())
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
            filter.add("tags", getTagsArray(searchTags))
            filter.add("subjects", getJsonArrayFromList(subjects))
            filter.add("language", getJsonArrayFromList(languages))
            filter.add("level", getJsonArrayFromList(levels))
            filter.add("mediaType", getJsonArrayFromList(mediums))
            activity.filter = Gson().toJson(filter)
            mRealm.commitTransaction()
        }
    }

    private fun recreateFragment(fragment: Fragment) {
        if (isAdded && activity != null && !requireActivity().isFinishing) {
            val transaction = parentFragmentManager.beginTransaction()
            if (isMyCourseLib) {
                val args = Bundle().apply {
                    putBoolean("isMyCourseLib", true)
                }
                fragment.arguments = args
            }
            transaction.replace(R.id.fragment_container, fragment)
            transaction.addToBackStack(null)
            transaction.commit()
        }
    }


    private fun additionalSetup() {
        val bottomSheet = requireView().findViewById<View>(R.id.card_filter)
        requireView().findViewById<View>(R.id.filter).setOnClickListener {
            bottomSheet.visibility = if (bottomSheet.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        orderByDate = requireView().findViewById(R.id.order_by_date_button)
        orderByTitle = requireView().findViewById(R.id.order_by_title_button)
        requireView().findViewById<View>(R.id.filterCategories).setOnClickListener {
            val f = ResourcesFilterFragment()
            f.setListener(this)
            f.show(childFragmentManager, "")
            bottomSheet.visibility = View.GONE
        }
        orderByDate.setOnClickListener { adapterLibrary.toggleSortOrder() }
        orderByTitle.setOnClickListener { adapterLibrary.toggleTitleSortOrder() }
    }
}