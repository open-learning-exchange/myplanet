package org.ole.planet.myplanet.base

import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.realm.Case
import io.realm.RealmModel
import io.realm.RealmObject
import io.realm.RealmResults
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.createMyCourse
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.getMyCourse
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.getMyCourseByUserId
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.getOurCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.createFromResource
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.getMyLibraryByUserId
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.getOurLibrary
import org.ole.planet.myplanet.model.RealmRemovedLog.Companion.onAdd
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Utilities.log
import org.ole.planet.myplanet.utilities.Utilities.toast
import java.util.Locale

abstract class BaseRecyclerFragment<LI> : BaseRecyclerParentFragment<Any?>(), OnRatingChangeListener {
    var subjects: MutableSet<String> = mutableSetOf()
    var languages: MutableSet<String> = mutableSetOf()
    var mediums: MutableSet<String> = mutableSetOf()
    var levels: MutableSet<String> = mutableSetOf()
    var selectedItems: MutableList<LI>? = null
    var gradeLevel = ""
    var subjectLevel = ""
    private lateinit var realmService: DatabaseService
    lateinit var recyclerView: RecyclerView
    lateinit var tvMessage: TextView
    lateinit var tvFragmentInfo: TextView
    var tvDelete: TextView? = null
    var list: MutableList<LI>? = null

    abstract fun getLayout(): Int

    abstract fun getAdapter(): RecyclerView.Adapter<*>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            isMyCourseLib = it.getBoolean("isMyCourseLib")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(getLayout(), container, false)
        settings = requireActivity().getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        recyclerView = v.findViewById(R.id.recycler)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        if (isMyCourseLib) {
            tvDelete = v.findViewById(R.id.tv_delete)
            initDeleteButton()
            v.findViewById<TextView>(R.id.tv_add)?.visibility = View.GONE
        }
        tvMessage = v.findViewById(R.id.tv_message)
        selectedItems = mutableListOf()
        list = mutableListOf()
        realmService = DatabaseService(requireActivity())
        mRealm = realmService.realmInstance
        profileDbHandler = UserProfileDbHandler(requireActivity())
        model = profileDbHandler.userModel!!
        recyclerView.adapter = getAdapter()
        if (isMyCourseLib) showDownloadDialog(getLibraryList(mRealm))
        return v
    }

    private fun initDeleteButton() {
        tvDelete?.let {
            it.visibility = View.VISIBLE
            it.setOnClickListener { deleteSelected(false) }
        }
    }

    override fun onRatingChanged() {
        recyclerView.adapter = getAdapter()
    }

    fun addToMyList() {
        for (i in selectedItems?.indices!!) {
            val `object` = selectedItems?.get(i) as RealmObject
            if (`object` is RealmMyLibrary) {
                val myObject = mRealm.where(RealmMyLibrary::class.java)
                    .equalTo("resourceId", `object`.resourceId).findFirst()
                createFromResource(myObject, mRealm, model.id)
                onAdd(mRealm, "resources", profileDbHandler.userModel?.id, myObject?.resourceId)
                toast(activity, getString(R.string.added_to_my_library))
            } else {
                val myObject = getMyCourse(mRealm, (`object` as RealmMyCourse).courseId)
                createMyCourse(myObject, mRealm, model.id)
                onAdd(mRealm, "courses", profileDbHandler.userModel?.id, myObject?.courseId)
                toast(activity, getString(R.string.added_to_my_courses))
            }
        }
        recyclerView.adapter = getAdapter()
        showNoData(tvMessage, getAdapter().itemCount)
    }

    fun deleteSelected(deleteProgress: Boolean) {
        for (i in selectedItems?.indices!!) {
            if (!mRealm.isInTransaction()) mRealm.beginTransaction()
            val `object` = selectedItems?.get(i) as RealmObject
            deleteCourseProgress(deleteProgress, `object`)
            removeFromShelf(`object`)
            recyclerView.adapter = getAdapter()
            showNoData(tvMessage, getAdapter().itemCount)
        }
    }

    private fun deleteCourseProgress(deleteProgress: Boolean, `object`: RealmObject) {
        if (deleteProgress && `object` is RealmMyCourse) {
            mRealm.where(RealmCourseProgress::class.java).equalTo("courseId", `object`.courseId)
                .findAll().deleteAllFromRealm()
            val examList: List<RealmStepExam> = mRealm.where(
                RealmStepExam::class.java
            ).equalTo("courseId", `object`.courseId).findAll()
            for (exam in examList) {
                mRealm.where(RealmSubmission::class.java).equalTo("parentId", exam.id)
                    .notEqualTo("type", "survey").equalTo("uploaded", false).findAll()
                    .deleteAllFromRealm()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mRealm.close()
    }

    private fun checkAndAddToList(
        course: RealmMyCourse?,
        courses: MutableList<RealmMyCourse>,
        tags: List<RealmTag>
    ) {
        for (tg in tags) {
            val count =
                mRealm.where(RealmTag::class.java).equalTo("db", "courses").equalTo("tagId", tg.id)
                    .equalTo("linkId", course?.courseId).count()
            if (count > 0 && !courses.contains(course)) course?.let { courses.add(it) }
        }
    }

//    private fun <LI : RealmModel> getData(s: String, c: Class<LI>): List<LI> {
//        val li: MutableList<LI>
//        if (!s.contains(" ")) {
//            li = mRealm.where(c).contains(
//                if (c == RealmMyLibrary::class.java) "title" else "courseTitle",
//                s, Case.INSENSITIVE
//            ).findAll()
//        } else {
//            val query = s.split(" ").filterNot { it.isEmpty() }
//            val data: RealmResults<LI> = mRealm.where(c).findAll()
//            // Create a snapshot of data to work with a stable collection
//            li = data.toList().toMutableList()
//            li.clear() // Clear the mutable list to fill it in the loop below
//            for (l in data) {
//                searchAndAddToList(l, c, query, li)
//            }
//        }
//        return li
//    }
//
//    private fun searchAndAddToList(l: LI, c: Class<out RealmModel>, query: Array<String>, li: MutableList<LI>) {
//        val title = if (c.isAssignableFrom(RealmMyLibrary::class.java)) (l as RealmMyLibrary).title else (l as RealmMyCourse).courseTitle
//        var isExists = false
//        for (q in query) {
//            isExists = title?.lowercase(Locale.getDefault())?.contains(q.lowercase(Locale.getDefault())) == true
//            if (!isExists) break
//        }
//        if (isExists) li.add(l)
//    }

    private fun <LI : RealmModel> getData(s: String, c: Class<LI>): List<LI> {
        val queryParts = s.split(" ").filterNot { it.isEmpty() }
        return if (s.contains(" ")) {
            val data: RealmResults<LI> = mRealm.where(c).findAll()
            data.filter { item ->
                searchAndMatch(item, c, queryParts)
            }
        } else {
            mRealm.where(c).contains(
                if (c == RealmMyLibrary::class.java) "title" else "courseTitle",
                s, Case.INSENSITIVE
            ).findAll()
        }
    }

    private fun <LI : RealmModel> searchAndMatch(item: LI, c: Class<out RealmModel>, queryParts: List<String>): Boolean {
        val title = if (c.isAssignableFrom(RealmMyLibrary::class.java)) (item as RealmMyLibrary).title else (item as RealmMyCourse).courseTitle
        return queryParts.all { queryPart ->
            title?.lowercase(Locale.getDefault())?.contains(queryPart.lowercase(Locale.getDefault())) == true
        }
    }

    fun filterLibraryByTag(s: String, tags: List<RealmTag>): List<RealmMyLibrary> {
        // No need for casting if getData returns the correct type
        var list = getData(s, RealmMyLibrary::class.java)
        list = if (isMyCourseLib) {
            // Assuming model.id is a String? that represents the user ID
            getMyLibraryByUserId(model.id, list)
        } else {
            getOurLibrary(model.id, list) // Assuming getOurLibrary is implemented similarly
        }
        if (tags.isEmpty()) return list

        // Assuming filter(...) is correctly implemented to work with non-nullable lists
        val libraries = mutableListOf<RealmMyLibrary>()
        for (library in list) {
            filter(tags, library, libraries)
        }
        return libraries
    }




//    private fun getData(s: String, c: Class<*>): List<LI?> {
//        var li: MutableList<LI?> = ArrayList()
//        if (!s.contains(" ")) {
//            li = mRealm.where<RealmModel?>(c).contains(
//                if (c == RealmMyLibrary::class.java) {
//                    "title"
//                } else {
//                    "courseTitle"
//                }, s, Case.INSENSITIVE
//            ).findAll()
//        } else {
//            val query = s.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
//            val data: List<LI> = mRealm.where<RealmModel>(c).findAll()
//            for (l in data) {
//                searchAndAddToList(l, c, query, li)
//            }
//        }
//        return li
//    }

//    private fun searchAndAddToList(l: LI, c: Class<*>, query: List<String>, li: MutableList<LI?>) {
//        val title =
//            if (c == RealmMyLibrary::class.java) (l as RealmMyLibrary).title else (l as RealmMyCourse).courseTitle
//        var isExists = false
//        for (q in query) {
//            isExists =
//                title!!.lowercase(Locale.getDefault()).contains(q.lowercase(Locale.getDefault()))
//            log(title.lowercase(Locale.getDefault()) + " " + q.lowercase(Locale.getDefault()) + " is exists " + isExists)
//            if (!isExists) break
//        }
//        if (isExists) li.add(l)
//    }

//    fun filterLibraryByTag(s: String, tags: List<RealmTag>): List<RealmMyLibrary?> {
//        if (tags.isEmpty() && s.isEmpty()) {
//            return getList(RealmMyLibrary::class.java) as List<RealmMyLibrary?>
//        }
//        var list = getData(s, RealmMyLibrary::class.java) as List<RealmMyLibrary?>
//        if (isMyCourseLib) {
//            list = getMyLibraryByUserId(model.id, list)
//        } else {
//            list = getOurLibrary(model.id, list)
//        }
//        if (tags.isEmpty()) {
//            return list
//        }
//        val libraries = RealmList<RealmMyLibrary?>()
//        for (library in list) {
//            filter(tags, library, libraries)
//        }
//        return libraries
//    }

//    fun filterCourseByTag(s: String, tags: List<RealmTag>): List<RealmMyCourse?> {
//        if (tags.isEmpty() && s.isEmpty()) {
//            return applyCourseFilter(getList(RealmMyCourse::class.java) as List<RealmMyCourse?>)
//        }
//        var list = getData(s, RealmMyCourse::class.java) as RealmResults<RealmMyCourse?>
//        list = if (isMyCourseLib) {
//            getMyCourseByUserId(model.id, list) as RealmResults<RealmMyCourse?>
//        } else getOurCourse(model.id, list) as RealmResults<RealmMyCourse?>
//        if (tags.isEmpty()) return list
//        val courses = RealmList<RealmMyCourse?>()
//        for (course in list) {
//            checkAndAddToList(course, courses, tags)
//        }
//        return applyCourseFilter(list)
//    }

//    fun filterCourseByTag(s: String, tags: List<RealmTag>): List<RealmMyCourse> {
//        val initialList = if (s.isEmpty()) {
//            getList(RealmMyCourse::class.java) // This should return List<RealmMyCourse>
//        } else {
//            getData(s, RealmMyCourse::class.java) // Ensure getData returns List<RealmMyCourse>
//        }
//
//        val list: List<RealmMyCourse> =
//            if (isMyCourseLib) {
//                getMyCourseByUserId(model.id, initialList)
//            } else {
//                getOurCourse(model.id, initialList)
//            }
//
//        if (tags.isEmpty()) {
//            return applyCourseFilter(list)
//        }
//
//        val courses = mutableListOf<RealmMyCourse>()
//        for (course in list) {
//            checkAndAddToList(course, courses, tags)
//        }
//
//        return applyCourseFilter(courses)
//    }

    fun filterCourseByTag(s: String, tags: List<RealmTag>): List<RealmMyCourse> {
        // Initial call to getList, ensuring type safety through casting. Be cautious with casting.
        val initialList: List<RealmMyCourse> = getList(RealmMyCourse::class.java) as List<RealmMyCourse>

        // Adjusted usage of initialList based on its actual type now
        val list: List<RealmMyCourse> = if (isMyCourseLib) {
            getMyCourseByUserId(model.id, initialList)
        } else {
            getOurCourse(model.id, initialList)
        }

        if (tags.isEmpty()) {
            return applyCourseFilter(list)
        }

        val courses = mutableListOf<RealmMyCourse>()
        for (course in list) {
            checkAndAddToList(course, courses, tags)
        }

        return applyCourseFilter(courses)
    }



    private fun filter(tags: List<RealmTag>, library: RealmMyLibrary?, libraries: MutableList<RealmMyLibrary>) {
        for (tg in tags) {
            val count = mRealm.where(RealmTag::class.java).equalTo("db", "resources")
                .equalTo("tagId", tg.id).equalTo("linkId", library?.id).count()
            if (count > 0 && !libraries.contains(library)) {
                library?.let { libraries.add(it) }
            }
        }
    }

    fun applyFilter(libraries: List<RealmMyLibrary>): List<RealmMyLibrary> {
        val newList: MutableList<RealmMyLibrary> = ArrayList()
        for (l in libraries) {
            if (isValidFilter(l)) newList.add(l)
        }
        return newList
    }

    private fun applyCourseFilter(courses: List<RealmMyCourse>): List<RealmMyCourse> {
        log("apply course filter")
        if (TextUtils.isEmpty(subjectLevel) && TextUtils.isEmpty(gradeLevel)) return courses
        val newList: MutableList<RealmMyCourse> = ArrayList()
        for (l in courses) {
            log("grade $gradeLevel")
            log("subject $subjectLevel")
            if (TextUtils.equals(l.gradeLevel, gradeLevel) || TextUtils.equals(
                    l.subjectLevel, subjectLevel
                )
            ) {
                newList.add(l)
            }
        }
        return newList
    }

    private fun isValidFilter(l: RealmMyLibrary): Boolean {
        val sub = subjects.isEmpty() || subjects.let { l.subject?.containsAll(it) } == true
        val lev = levels.isEmpty() || l.level!!.containsAll(levels)
        val lan = languages.isEmpty() || languages.contains(l.language)
        val med = mediums.isEmpty() || mediums.contains(l.mediaType)
        return sub && lev && lan && med
    }

    companion object {
        const val PREFS_NAME = "OLE_PLANET"
        lateinit var settings: SharedPreferences

        fun showNoData(v: View?, count: Int?) {
            v ?: return
            v.visibility = if (count == 0) View.VISIBLE else View.GONE
            (v as TextView).setText(R.string.no_data_available_please_check_and_try_again)
        }

        fun showNoFilter(v: View?, count: Int) {
            v ?: return
            v.visibility = if (count == 0) View.VISIBLE else View.GONE
            (v as TextView).setText(R.string.no_course_matched_filter)
        }
    }
}
