package org.ole.planet.myplanet.ui.resources

import android.app.AlertDialog
import android.content.DialogInterface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
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
import org.ole.planet.myplanet.utilities.KeyboardUtils.setupUI
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.Utilities.getItemsPerPageValue
import java.util.Calendar
import java.util.UUID

class ResourcesFragment : BaseRecyclerFragment<RealmMyLibrary?>(), OnLibraryItemSelected,
    ChipDeletedListener, TagClickListener, OnFilterListener {
    private lateinit var tvAddToLib: TextView
    private lateinit var tvSelected: TextView
    private lateinit var etSearch: EditText
    private lateinit var etTags: EditText
    private lateinit var adapterResource: AdapterResource
    private lateinit var flexBoxTags: FlexboxLayout
    private lateinit var searchTags: MutableList<RealmTag>
    var config: ChipCloudConfig? = null
    private lateinit var clearTags: Button
    private lateinit var orderByTitle: Button
    private lateinit var orderByDate: Button
    private lateinit var selectAll: CheckBox
    var map: HashMap<String?, JsonObject>? = null
    private var confirmation: AlertDialog? = null
    var filter: ImageButton? = null
    private lateinit var btnPrevious: TextView
    private lateinit var btnNext: TextView
    private lateinit var spnItemsPerPage: Spinner
    private lateinit var ltPagination: LinearLayout
    private var drawableNext: Drawable? = null
    private var drawablePrevious: Drawable? = null

    override fun getLayout(): Int {
        return R.layout.fragment_my_library
    }

    override fun getAdapter(): RecyclerView.Adapter<*> {
        map = getRatings(mRealm, "resource", model.id)
        val libraryList: List<RealmMyLibrary?> = getList(RealmMyLibrary::class.java).filterIsInstance<RealmMyLibrary?>()
        adapterResource = AdapterResource(requireActivity(), libraryList, map!!, mRealm)
        adapterResource.setRatingChangeListener(this)
        adapterResource.setListener(this)
        return adapterResource
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        searchTags = ArrayList()
        config = Utilities.getCloudConfig().showClose(R.color.black_overlay)
        tvAddToLib = requireView().findViewById(R.id.tv_add)
        etSearch = requireView().findViewById(R.id.et_search)
        etTags = requireView().findViewById(R.id.et_tags)
        clearTags = requireView().findViewById(R.id.btn_clear_tags)
        tvSelected = requireView().findViewById(R.id.tv_selected)
        flexBoxTags = requireView().findViewById(R.id.flexbox_tags)
        selectAll = requireView().findViewById(R.id.selectAll)
        tvDelete = requireView().findViewById(R.id.tv_delete)
        filter = requireView().findViewById(R.id.filter)
        btnNext = requireView().findViewById(R.id.next)
        btnPrevious = requireView().findViewById(R.id.previous)
        spnItemsPerPage = requireView().findViewById(R.id.spn_items_per_page)
        ltPagination = requireView().findViewById(R.id.ltPagination)

        initArrays()
        tvAddToLib.setOnClickListener {
            if ((selectedItems?.size ?: 0) > 0) {
                confirmation = createAlertDialog()
                confirmation?.show()
                addToMyList()
                selectedItems?.clear()
                tvAddToLib.isEnabled = false // After clearing selectedItems size is always 0
                checkList()
            }
        }
        tvDelete?.setOnClickListener {
            AlertDialog.Builder(this.context)
                .setMessage(R.string.confirm_removal)
                .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                    deleteSelected(true)
                    val newFragment = ResourcesFragment()
                    recreateFragment(newFragment)
                }
                .setNegativeButton(R.string.no, null).show()
        }
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                adapterResource.setLibraryList(applyFilter(filterLibraryByTag(etSearch.text.toString().trim { it <= ' ' }, searchTags)))
                showNoData(tvMessage, adapterResource.itemCount, "resources")
            }

            override fun afterTextChanged(s: Editable) {}
        })
        requireView().findViewById<View>(R.id.btn_collections).setOnClickListener {
            val f = CollectionsFragment.getInstance(searchTags, "resources")
            f.setListener(this@ResourcesFragment)
            f.show(childFragmentManager, "")
        }
        showNoData(tvMessage, adapterResource.itemCount, "resources")
        clearTagsButton()
        setupUI(requireView().findViewById(R.id.my_library_parent_layout), requireActivity())
        changeButtonStatus()
        additionalSetup()
        tvFragmentInfo = requireView().findViewById(R.id.tv_fragment_info)
        if (isMyCourseLib) tvFragmentInfo.setText(R.string.txt_myLibrary)
        checkList()
        selectAll.setOnClickListener {
            val allSelected = selectedItems?.size == adapterResource.getLibraryList().size
            adapterResource.selectAllItems(!allSelected)
            if (allSelected) {
                selectAll.isChecked = false
                selectAll.text = getString(R.string.select_all)
            } else {
                selectAll.isChecked = true
                selectAll.text = getString(R.string.unselect_all)
            }
        }

        drawableNext = ContextCompat.getDrawable(requireContext(), R.drawable.ic_right_arrow)
        drawableNext?.setTint(ContextCompat.getColor(requireContext(), R.color.md_black_1000))
        btnNext.setCompoundDrawablesWithIntrinsicBounds(null, null, drawableNext, null)

        drawablePrevious = ContextCompat.getDrawable(requireContext(), R.drawable.ic_left_arrow)
        drawablePrevious?.setTint(ContextCompat.getColor(requireContext(), R.color.md_black_1000))
        btnPrevious.setCompoundDrawablesWithIntrinsicBounds(drawablePrevious, null, null, null)

        spnItemsPerPage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedValue = parent.getItemAtPosition(position).toString()
                val itemsPerPage = getItemsPerPageValue(selectedValue)
                adapterResource.itemsPerPage = itemsPerPage
                adapterResource.currentPage = 1
                adapterResource.clearSelection()
                adapterResource.notifyDataSetChanged()
                updateButtonVisibility()
                selectAll.isChecked = false
                selectAll.text = getString(R.string.select_all)
                recyclerView.scrollToPosition(0)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        btnNext.setOnClickListener {
            if (adapterResource.currentPage * adapterResource.itemsPerPage < adapterResource.getTotalResourceCount()) {
                adapterResource.currentPage++
                adapterResource.clearSelection()
                adapterResource.notifyDataSetChanged()
                updateButtonVisibility()
                selectAll.isChecked = false
                selectAll.text = getString(R.string.select_all)
                recyclerView.scrollToPosition(0)
            }
        }

        btnPrevious.setOnClickListener {
            if (adapterResource.currentPage > 1) {
                adapterResource.currentPage--
                adapterResource.clearSelection()
                adapterResource.notifyDataSetChanged()
                updateButtonVisibility()
                selectAll.isChecked = false
                selectAll.text = getString(R.string.select_all)
                recyclerView.scrollToPosition(0)
            }
        }
        updateButtonVisibility()
    }

    private fun updateButtonVisibility() {
        if (adapterResource.itemsPerPage == Int.MAX_VALUE) {
            btnNext.visibility = View.GONE
            btnPrevious.visibility = View.GONE
        } else {
            if (adapterResource.currentPage < adapterResource.getTotalPages()) {
                btnNext.isEnabled = true
                btnNext.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_black_1000))
                drawableNext?.setTint(ContextCompat.getColor(requireContext(), R.color.md_black_1000))
            } else {
                btnNext.isEnabled = false
                btnNext.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_grey_500))
                drawableNext?.setTint(ContextCompat.getColor(requireContext(), R.color.md_grey_500))
            }
            if (adapterResource.currentPage > 1) {
                btnPrevious.isEnabled = true
                btnPrevious.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_black_1000))
                drawablePrevious?.setTint(ContextCompat.getColor(requireContext(), R.color.md_black_1000))
            } else {
                btnPrevious.isEnabled = false
                btnPrevious.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_grey_500))
                drawablePrevious?.setTint(ContextCompat.getColor(requireContext(), R.color.md_grey_500))
            }
        }

        if (adapterResource.itemCount == 0) {
            ltPagination.visibility = View.GONE
            tvDelete?.visibility = View.GONE
        }
    }

    private fun checkList() {
        if (adapterResource.getLibraryList().isEmpty()) {
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
        val builder = AlertDialog.Builder(requireContext(), 5)
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
        builder.setPositiveButton(getString(R.string.ok)) { dialog: DialogInterface, _: Int ->
            dialog.cancel()
            val newFragment = ResourcesFragment()
            recreateFragment(newFragment)
        }
        return builder.create()
    }

    private fun clearTagsButton() {
        clearTags.setOnClickListener {
            saveSearchActivity()
            searchTags.clear()
            etSearch.setText("")
            tvSelected.text = ""
            levels.clear()
            mediums.clear()
            subjects.clear()
            languages.clear()
            adapterResource.setLibraryList(applyFilter(filterLibraryByTag("", searchTags)))
            showNoData(tvMessage, adapterResource.itemCount, "resources")
        }
    }

    override fun onSelectedListChange(list: MutableList<RealmMyLibrary?>) {
        selectedItems = list
        changeButtonStatus()
    }

    override fun onTagClicked(realmTag: RealmTag) {
        flexBoxTags.removeAllViews()
        val chipCloud = ChipCloud(activity, flexBoxTags, config)
        chipCloud.setDeleteListener(this)
        if (!searchTags.contains(realmTag)) searchTags.add(realmTag)
        chipCloud.addChips(searchTags)
        adapterResource.setLibraryList(applyFilter(filterLibraryByTag(etSearch.text.toString(), searchTags)))
        showTagText(searchTags, tvSelected)
        showNoData(tvMessage, adapterResource.itemCount, "resources")
    }

    override fun onTagSelected(tag: RealmTag) {
        val li: MutableList<RealmTag> = ArrayList()
        li.add(tag)
        searchTags = li
        tvSelected.text = "${getString(R.string.selected)}${tag.name}"
        adapterResource.setLibraryList(applyFilter(filterLibraryByTag(etSearch.text.toString(), li)))
        showNoData(tvMessage, adapterResource.itemCount, "resources")
    }

    override fun onOkClicked(list: List<RealmTag>?) {
        if (list?.isEmpty() == true) {
            searchTags.clear()
            adapterResource.setLibraryList(applyFilter(filterLibraryByTag(etSearch.text.toString(), searchTags)))
            showNoData(tvMessage, adapterResource.itemCount, "resources")
        } else {
            for (tag in list ?: emptyList()) {
                onTagClicked(tag)
            }
        }
    }

    private fun changeButtonStatus() {
        tvAddToLib.isEnabled = (selectedItems?.size ?: 0) > 0
        if (adapterResource.areAllSelected()) {
            selectAll.isChecked = true
            selectAll.text = getString(R.string.unselect_all)
        } else {
            selectAll.isChecked = false
            selectAll.text = getString(R.string.select_all)
        }
    }

    override fun chipDeleted(i: Int, s: String) {
        searchTags.removeAt(i)
        adapterResource.setLibraryList(applyFilter(filterLibraryByTag(etSearch.text.toString(), searchTags)))
        showNoData(tvMessage, adapterResource.itemCount, "resources")
    }

    override fun filter(subjects: MutableSet<String>, languages: MutableSet<String>, mediums: MutableSet<String>, levels: MutableSet<String>) {
        this.subjects = subjects
        this.languages = languages
        this.mediums = mediums
        this.levels = levels
        adapterResource.setLibraryList(applyFilter(filterLibraryByTag(etSearch.text.toString().trim { it <= ' ' }, searchTags)))
        showNoData(tvMessage, adapterResource.itemCount, "resources")
    }

    override fun getData(): Map<String, Set<String>> {
        val libraryList = adapterResource.getLibraryList()?.filterNotNull()
        val b: MutableMap<String, Set<String>> = HashMap()
        b["languages"] = libraryList?.let { getArrayList(it, "languages").filterNotNull().toSet() }!!
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
            activity.user = model.name!!
            activity.time = Calendar.getInstance().timeInMillis
            activity.createdOn = model.planetCode!!
            activity.parentCode = model.parentCode!!
            activity.text = etSearch.text.toString()
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
        if (isMyCourseLib) {
            val args = Bundle()
            args.putBoolean("isMyCourseLib", true)
            fragment.arguments = args
            val transaction = parentFragmentManager.beginTransaction()
            transaction.replace(R.id.fragment_container, fragment)
            transaction.addToBackStack(null)
            transaction.commit()
        } else {
            val transaction = parentFragmentManager.beginTransaction()
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
        orderByDate.setOnClickListener { adapterResource.toggleSortOrder() }
        orderByTitle.setOnClickListener { adapterResource.toggleTitleSortOrder() }
    }
}