package org.ole.planet.myplanet.ui.courses

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.*
import android.view.View
import android.widget.*
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.callback.*
import org.ole.planet.myplanet.model.*
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.resources.CollectionsFragment
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.KeyboardUtils.setupUI
import java.util.*

class CoursesFragment : BaseRecyclerFragment<RealmMyCourse?>(), OnCourseItemSelected, TagClickListener {

    companion object {
        fun newInstance(isMyCourseLib: Boolean): CoursesFragment {
            val fragment = CoursesFragment()
            val args = Bundle()
            args.putBoolean("isMyCourseLib", isMyCourseLib)
            fragment.arguments = args
            return fragment
        }
    }

    private lateinit var tvAddToLib: TextView
    private lateinit var tvSelected: TextView
    private lateinit var etSearch: EditText
    private lateinit var adapterCourses: AdapterCourses
    private lateinit var btnRemove: Button
    private lateinit var btnArchive: Button
    private lateinit var orderByDate: Button
    private lateinit var orderByTitle: Button
    private lateinit var selectAll: CheckBox
    private lateinit var spnGrade: Spinner
    private lateinit var spnSubject: Spinner
    private lateinit var searchTags: MutableList<RealmTag>
    private lateinit var confirmation: AlertDialog
    var userModel: RealmUserModel? = null
    private val scope = MainApplication.applicationScope

    override fun getLayout(): Int {
        return R.layout.fragment_my_course
    }

    override fun getAdapter(): RecyclerView.Adapter<*> {
        val courseList: List<RealmMyCourse?> = getList(RealmMyCourse::class.java).filterIsInstance<RealmMyCourse?>()
        val sortedCourseList = courseList.sortedWith(compareBy({ it?.isMyCourse }, { it?.courseTitle }))

        scope.launch {
            val ratingsMap = HashMap<String?, JsonObject>(RealmRating.getRatings(mRealm, "course", model?.id))
            adapterCourses = AdapterCourses(requireActivity(), sortedCourseList, ratingsMap)
            adapterCourses.setmRealm(mRealm)
            adapterCourses.setListener(this@CoursesFragment)
            adapterCourses.setRatingChangeListener(this@CoursesFragment)

            RealmCourseProgress.getCourseProgress(mRealm, model?.id ?: "").collectLatest { progress ->
                val progressMap = HashMap<String?, JsonObject>(progress)
                adapterCourses.setProgressMap(progressMap)

                if (isMyCourseLib) {
                    val courseIds = courseList.mapNotNull { it?.id }.toTypedArray()
                    resources = mRealm.query<RealmMyLibrary>(RealmMyLibrary::class, "courseId IN $0 AND resourceOffline == false AND resourceLocalAddress != null", courseIds).find()
                    courseLib = "courses"
                }
            }
        }

        return adapterCourses
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userModel = UserProfileDbHandler(requireContext()).userModel
        searchTags = ArrayList()
        initializeView()
        if (isMyCourseLib) {
            btnRemove.visibility = View.VISIBLE
            btnArchive.visibility = View.VISIBLE
            checkList()
        }else {
            btnRemove.visibility = View.GONE
            btnArchive.visibility = View.GONE
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                adapterCourses.setCourseList(filterCourseByTag(etSearch.text.toString(), searchTags))
                showNoData(tvMessage, adapterCourses.itemCount, "courses")
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
                    scope.launch {
                        deleteSelected(true)
                    }
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
                    scope.launch {
                        deleteSelected(true)
                    }
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

        clearTags()
        showNoData(tvMessage, adapterCourses.itemCount, "courses")
        setupUI(requireView().findViewById(R.id.my_course_parent_layout), requireActivity())
        changeButtonStatus()
        if (!isMyCourseLib) tvFragmentInfo.setText(R.string.our_courses)
        additionalSetup()

        if (isMyCourseLib) {
            requireView().findViewById<View>(R.id.fabMyProgress).apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    val myProgressFragment = MyProgressFragment().apply {
                        arguments = Bundle().apply {
                            putBoolean("isMyCourseLib", true)
                        }
                    }

                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, myProgressFragment)
                        .addToBackStack(null)
                        .commit()
                }
            }
        }
    }

    private fun additionalSetup() {
        val bottomSheet = requireView().findViewById<View>(R.id.card_filter)
        requireView().findViewById<View>(R.id.filter).setOnClickListener {
            bottomSheet.visibility = if (bottomSheet.visibility == View.VISIBLE) View.GONE else View.VISIBLE
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
                scope.launch {
                    addToMyList()
                }
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
        val gradeAdapter = ArrayAdapter.createFromResource(requireContext(), R.array.grade_level, R.layout.spinner_item)
        gradeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spnGrade.adapter = gradeAdapter

        val subjectAdapter = ArrayAdapter.createFromResource(requireContext(), R.array.subject_level, R.layout.spinner_item)
        subjectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spnSubject.adapter = subjectAdapter

        spnGrade.onItemSelectedListener = itemSelectedListener
        spnSubject.onItemSelectedListener = itemSelectedListener
        selectAll = requireView().findViewById(R.id.selectAll)
        if (userModel?.isGuest() == true) {
            tvAddToLib.visibility = View.GONE
            btnRemove.visibility = View.GONE
            btnArchive.visibility = View.GONE
            selectAll.visibility = View.GONE
        }
        checkList()
        selectAll.setOnClickListener {
            val allSelected = selectedItems?.size == adapterCourses.getCourseList().size
            adapterCourses.selectAllItems(!allSelected)
            if (allSelected) {
                selectAll.isChecked = false
                selectAll.text = getString(R.string.select_all)
            } else {
                selectAll.isChecked = true
                selectAll.text = getString(R.string.unselect_all)
            }
        }
        checkList()
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
            val allMyCourses = adapterCourses.getCourseList().all { it?.isMyCourse == true }
            selectAll.visibility = if (allMyCourses) View.GONE else View.VISIBLE
        }
    }

    private val itemSelectedListener: AdapterView.OnItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, i: Int, l: Long) {
            if (view == null) {
                return
            }
            gradeLevel = if (spnGrade.selectedItem.toString() == "All") "" else spnGrade.selectedItem.toString()
            subjectLevel = if (spnSubject.selectedItem.toString() == "All") "" else spnSubject.selectedItem.toString()
            adapterCourses.setCourseList(filterCourseByTag(etSearch.text.toString(), searchTags))
            showNoFilter(tvMessage, adapterCourses.itemCount)
        }

        override fun onNothingSelected(adapterView: AdapterView<*>?) {}
    }

    private fun clearTags() {
        requireView().findViewById<View>(R.id.btn_clear_tags).setOnClickListener {
            searchTags.clear()
            etSearch.setText(R.string.empty_text)
            tvSelected.text = context?.getString(R.string.empty_text)
            adapterCourses.setCourseList(filterCourseByTag("", searchTags))
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
                    redirectToMyCourses()
                }
            }
            .setNegativeButton(R.string.ok) { dialog: DialogInterface, _: Int ->
                dialog.cancel()
                val newFragment = CoursesFragment()
                recreateFragment(newFragment)
            }

        return builder.create()
    }

    fun redirectToMyCourses() {
        val fragment = newInstance(isMyCourseLib = true)

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onSelectedListChange(list: MutableList<RealmMyCourse?>) {
        selectedItems = list
        changeButtonStatus()
    }

    override fun onTagClicked(tag: RealmTag?) {
        if (!searchTags.contains(tag)) {
            tag?.let { searchTags.add(it) }
        }
        adapterCourses.setCourseList(filterCourseByTag(etSearch.text.toString(), searchTags))
        showTagText(searchTags, tvSelected)
        showNoData(tvMessage, adapterCourses.itemCount, "courses")
    }

    private fun changeButtonStatus() {
        tvAddToLib.isEnabled = (selectedItems?.size ?: 0) > 0
        btnRemove.isEnabled = (selectedItems?.size ?: 0) > 0
        btnArchive.isEnabled = (selectedItems?.size ?: 0) > 0
        if (adapterCourses.areAllSelected()) {
            selectAll.isChecked = true
            selectAll.text = getString(R.string.unselect_all)
        } else {
            selectAll.isChecked = false
            selectAll.text = getString(R.string.select_all)
        }
    }

    override fun onTagSelected(tag: RealmTag) {
        val li: MutableList<RealmTag> = ArrayList()
        li.add(tag)
        searchTags = li
        tvSelected.text = context?.getString(R.string.tag_selected, tag.name)
        adapterCourses.setCourseList(filterCourseByTag(etSearch.text.toString(), li))
        showNoData(tvMessage, adapterCourses.itemCount, "courses")
    }

    override fun onOkClicked(list: List<RealmTag>?) {
        if (list?.isEmpty() == true) {
            searchTags.clear()
            adapterCourses.setCourseList(filterCourseByTag(etSearch.text.toString(), searchTags))
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
        if (!filterApplied()) return

        scope.launch {
            mRealm.write {
                copyToRealm(RealmSearchActivity().apply {
                    id = UUID.randomUUID().toString()
                    user = model?.name ?: ""
                    time = Calendar.getInstance().timeInMillis
                    createdOn = model?.planetCode ?: ""
                    parentCode = model?.parentCode ?: ""
                    text = etSearch.text.toString()
                    type = "courses"

                    val filter = JsonObject().apply {
                        add("tags", RealmTag.getTagsArray(searchTags.toList()))
                        addProperty("doc.gradeLevel", gradeLevel)
                        addProperty("doc.subjectLevel", subjectLevel)
                    }
                    this.filter = Gson().toJson(filter)
                })
            }
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
            args.putString("courseLib", courseLib)
            args.putSerializable("resources", resources?.let { ArrayList(it) })
            fragment.arguments = args
            val transaction = parentFragmentManager.beginTransaction()
            transaction.replace(R.id.fragment_container, fragment)
            transaction.addToBackStack(null)
            transaction.commitAllowingStateLoss()
        } else {
            val transaction = parentFragmentManager.beginTransaction()
            transaction.replace(R.id.fragment_container, fragment)
            transaction.addToBackStack(null)
            transaction.commitAllowingStateLoss()
        }
    }
}
