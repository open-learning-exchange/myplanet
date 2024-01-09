package org.ole.planet.myplanet.ui.course

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.callback.OnCourseItemSelected
import org.ole.planet.myplanet.callback.TagClickListener
import org.ole.planet.myplanet.model.RealmCourseProgress.Companion.getCourseProgress
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmRating.Companion.getRatings
import org.ole.planet.myplanet.model.RealmSearchActivity
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmTag.Companion.getTagsArray
import org.ole.planet.myplanet.ui.library.CollectionsFragment
import org.ole.planet.myplanet.utilities.KeyboardUtils.setupUI
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Calendar
import java.util.UUID

class CourseFragment : BaseRecyclerFragment<RealmMyCourse?>(), OnCourseItemSelected, TagClickListener {
    private var tvAddToLib: TextView? = null
    private var tvSelected: TextView? = null
    var etSearch: EditText? = null
    var adapterCourses: AdapterCourses? = null
    private var btnRemove: Button? = null
    private var orderByDate: Button? = null
    private var orderByTitle: Button? = null
    private var selectAll: CheckBox? = null
    var spnGrade: Spinner? = null
    var spnSubject: Spinner? = null
    var searchTags: MutableList<RealmTag?>? = null
    private var confirmation: AlertDialog? = null
    override fun getLayout(): Int {
        return R.layout.fragment_my_course
    }

    override fun getAdapter(): RecyclerView.Adapter<*> {
        val map = mRealm?.let { getRatings(it, "course", model?.id) }
        val progressMap = mRealm?.let { getCourseProgress(it, model?.id) }
        adapterCourses = map?.let { AdapterCourses(requireActivity(), getList(RealmMyCourse::class.java) as List<RealmMyCourse?>, it) }
        adapterCourses!!.setProgressMap(progressMap)
        adapterCourses!!.setmRealm(mRealm)
        adapterCourses!!.setListener(this)
        adapterCourses!!.setRatingChangeListener(this)
        return adapterCourses!!
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        searchTags = ArrayList()
        initializeView()
        if (isMyCourseLib) {
            tvDelete.setText(R.string.archive)
            btnRemove!!.visibility = View.VISIBLE
        }

        etSearch!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                adapterCourses!!.setCourseList(filterCourseByTag(etSearch!!.text.toString(), searchTags))
                showNoData(tvMessage, adapterCourses!!.itemCount)
            }

            override fun afterTextChanged(s: Editable) {}
        })

        btnRemove!!.setOnClickListener {
            AlertDialog.Builder(this.context)
                .setMessage(R.string.are_you_sure_you_want_to_delete_these_courses)
                .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                    deleteSelected(true)
                    val newFragment = CourseFragment()
                    recreateFragment(newFragment)
                }
                .setNegativeButton(R.string.no, null).show()
        }

        requireView().findViewById<View>(R.id.btn_collections).setOnClickListener {
            val f = CollectionsFragment.getInstance(searchTags, "courses")
            f.setListener(this)
            f.show(childFragmentManager, "")
        }

        clearTags()
        showNoData(tvMessage, adapterCourses!!.itemCount)
        setupUI(requireView().findViewById(R.id.my_course_parent_layout), requireActivity())
        changeButtonStatus()
        if (!isMyCourseLib) tvFragmentInfo.setText(R.string.our_courses)
        additionalSetup()
    }

    private fun additionalSetup() {
        val bottomSheet = requireView().findViewById<View>(R.id.card_filter)
        requireView().findViewById<View>(R.id.filter).setOnClickListener {
            bottomSheet.visibility = if (bottomSheet.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        orderByDate = requireView().findViewById(R.id.order_by_date_button)
        orderByTitle = requireView().findViewById(R.id.order_by_title_button)
        orderByDate!!.setOnClickListener { adapterCourses!!.toggleSortOrder() }
        orderByTitle!!.setOnClickListener { adapterCourses!!.toggleTitleSortOrder() }
    }

    private fun initializeView() {
        tvAddToLib = requireView().findViewById(R.id.tv_add)
        tvAddToLib!!.setOnClickListener {
            if (selectedItems.size > 0) {
                confirmation = createAlertDialog()
                confirmation!!.show()
                addToMyList()
                selectedItems.clear()
                tvAddToLib!!.isEnabled = false // selectedItems will always have a size of 0
                checkList()
            }
        }
        etSearch = requireView().findViewById(R.id.et_search)
        tvSelected = requireView().findViewById(R.id.tv_selected)
        btnRemove = requireView().findViewById(R.id.btn_remove)
        spnGrade = requireView().findViewById(R.id.spn_grade)
        spnSubject = requireView().findViewById(R.id.spn_subject)
        tvMessage = requireView().findViewById(R.id.tv_message)
        requireView().findViewById<View>(R.id.tl_tags).visibility = View.GONE
        tvFragmentInfo = requireView().findViewById(R.id.tv_fragment_info)
        spnGrade!!.onItemSelectedListener = itemSelectedListener
        spnSubject!!.onItemSelectedListener = itemSelectedListener
        selectAll = requireView().findViewById(R.id.selectAll)
        checkList()
        selectAll!!.setOnClickListener {
            val allSelected = selectedItems.size == adapterCourses!!.getCourseList().size
            adapterCourses!!.selectAllItems(!allSelected)
            if (allSelected) {
                selectAll!!.isChecked = false
                selectAll!!.text = getString(R.string.select_all)
            } else {
                selectAll!!.isChecked = true
                selectAll!!.text = getString(R.string.unselect_all)
            }
        }
    }

    private fun checkList() {
        if (adapterCourses!!.getCourseList().isEmpty()) {
            selectAll!!.visibility = View.GONE
            etSearch!!.visibility = View.GONE
            tvAddToLib!!.visibility = View.GONE
            requireView().findViewById<View>(R.id.filter).visibility = View.GONE
            btnRemove!!.visibility = View.GONE
            tvSelected!!.visibility = View.GONE
        }
    }

    private val itemSelectedListener: AdapterView.OnItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
            Utilities.log("On item selected")
            gradeLevel = if (spnGrade!!.selectedItem.toString() == "All") "" else spnGrade!!.selectedItem.toString()
            subjectLevel = if (spnSubject!!.selectedItem.toString() == "All") "" else spnSubject!!.selectedItem.toString()
            adapterCourses!!.setCourseList(filterCourseByTag(etSearch!!.text.toString(), searchTags))
            showNoFilter(tvMessage, adapterCourses!!.itemCount)
        }

        override fun onNothingSelected(adapterView: AdapterView<*>?) {}
    }

    private fun clearTags() {
        requireView().findViewById<View>(R.id.btn_clear_tags).setOnClickListener {
            searchTags!!.clear()
            etSearch!!.setText("")
            tvSelected!!.text = ""
            adapterCourses!!.setCourseList(filterCourseByTag("", searchTags))
            showNoData(tvMessage, adapterCourses!!.itemCount)
            spnGrade!!.setSelection(0)
            spnSubject!!.setSelection(0)
        }
    }

    private fun createAlertDialog(): AlertDialog {
        val builder = AlertDialog.Builder(requireContext(), 5)
        var msg = getString(R.string.success_you_have_added_the_following_courses)
        if (selectedItems.size <= 5) {
            for (i in selectedItems.indices) {
                msg += " - " + selectedItems[i]!!.courseTitle + "\n"
            }
        } else {
            for (i in 0..4) {
                msg += " - " + selectedItems[i]!!.courseTitle + "\n"
            }
            msg += getString(R.string.and) + (selectedItems.size - 5) + getString(R.string.more_course_s)
        }
        msg += getString(R.string.return_to_the_home_tab_to_access_mycourses)
        builder.setMessage(msg)
        builder.setCancelable(true)
        builder.setPositiveButton(R.string.ok) { dialog: DialogInterface, _: Int ->
            dialog.cancel()
            val newFragment = CourseFragment()
            recreateFragment(newFragment)
        }
        return builder.create()
    }

    override fun onSelectedListChange(list: List<RealmMyCourse?>?) {
        selectedItems = list
        changeButtonStatus()
    }

    override fun onTagClicked(tag: RealmTag?) {
        if (!searchTags!!.contains(tag)) searchTags!!.add(tag)
        adapterCourses!!.setCourseList(filterCourseByTag(etSearch!!.text.toString(), searchTags))
        tvSelected?.let { showTagText(searchTags!!, it) }
        showNoData(tvMessage, adapterCourses!!.itemCount)
    }

    private fun changeButtonStatus() {
        tvAddToLib!!.isEnabled = selectedItems.size > 0
        if (adapterCourses!!.areAllSelected()) {
            selectAll!!.isChecked = true
            selectAll!!.text = getString(R.string.unselect_all)
        } else {
            selectAll!!.isChecked = false
            selectAll!!.text = getString(R.string.select_all)
        }
    }

    override fun onTagSelected(tag: RealmTag?) {
        val li: MutableList<RealmTag?> = ArrayList()
        li.add(tag)
        searchTags = li
        tvSelected!!.text = R.string.selected.toString() + tag!!.name
        adapterCourses!!.setCourseList(filterCourseByTag(etSearch!!.text.toString(), li))
        showNoData(tvMessage, adapterCourses!!.itemCount)
    }

    override fun onOkClicked(list: List<RealmTag?>?) {
        if (list!!.isEmpty()) {
            searchTags!!.clear()
            adapterCourses!!.setCourseList(filterCourseByTag(etSearch!!.text.toString(), searchTags))
            showNoData(tvMessage, adapterCourses!!.itemCount)
        } else {
            for (tag in list) {
                onTagClicked(tag)
            }
        }
    }

    private fun filterApplied(): Boolean {
        return !(searchTags!!.isEmpty() && gradeLevel.isEmpty() && subjectLevel.isEmpty() && etSearch!!.text.toString().isEmpty())
    }

    private fun saveSearchActivity() {
        if (filterApplied()) {
            if (!mRealm?.isInTransaction!!) mRealm!!.beginTransaction()
            val activity = mRealm!!.createObject(RealmSearchActivity::class.java, UUID.randomUUID().toString())
            activity.user = model?.name!!
            activity.time = Calendar.getInstance().timeInMillis
            activity.createdOn = model!!.planetCode!!
            activity.parentCode = model!!.parentCode!!
            activity.text = etSearch!!.text.toString()
            activity.type = "courses"
            val filter = JsonObject()

            filter.add("tags", getTagsArray(searchTags?.filterNotNull() ?: listOf()))
            filter.addProperty("doc.gradeLevel", gradeLevel)
            filter.addProperty("doc.subjectLevel", subjectLevel)
            activity.filter = Gson().toJson(filter)
            mRealm!!.commitTransaction()
        }
    }

    override fun onPause() {
        super.onPause()
        saveSearchActivity()
    }

    private fun recreateFragment(fragment: Fragment) {
        if (isMyCourseLib) {
            val args = Bundle()
            args.putBoolean("isMyCourseLib", true)
            fragment.arguments = args
            val transaction = requireFragmentManager().beginTransaction()
            transaction.replace(R.id.fragment_container, fragment)
            transaction.addToBackStack(null)
            transaction.commit()
        } else {
            val transaction = requireFragmentManager().beginTransaction()
            transaction.replace(R.id.fragment_container, fragment)
            transaction.addToBackStack(null)
            transaction.commit()
        }
    }
}
