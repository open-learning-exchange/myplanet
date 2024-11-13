package org.ole.planet.myplanet.base

import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import io.realm.Case
import io.realm.RealmList
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
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
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
    var resources: List<RealmMyLibrary>? = null
    var courseLib: String? = null

    abstract fun getLayout(): Int

    abstract fun getAdapter(): RecyclerView.Adapter<*>

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            isMyCourseLib = it.getBoolean("isMyCourseLib")
            courseLib = it.getString("courseLib")
            val json = it.getString("resources")
            resources = (json?.let {
                val type = object : TypeToken<ArrayList<RealmMyLibrary>>() {}.type
                Gson().fromJson<ArrayList<RealmMyLibrary>>(json, type)
            } ?: arrayListOf())
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
        realmService = DatabaseService()
        mRealm = realmService.realmInstance
        profileDbHandler = UserProfileDbHandler(requireActivity())
        model = profileDbHandler.userModel!!
        val adapter = getAdapter()
        recyclerView.adapter = adapter
        if (isMyCourseLib && adapter.itemCount != 0 && courseLib == "courses") {
            resources?.let { showDownloadDialog(it) }
        } else if (isMyCourseLib && courseLib == null && !isSurvey) {
            showDownloadDialog(getLibraryList(mRealm))
        }
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
                createFromResource(myObject, mRealm, model?.id)
                onAdd(mRealm, "resources", profileDbHandler.userModel?.id, myObject?.resourceId)
                toast(activity, getString(R.string.added_to_my_library))
            } else {
                val myObject = getMyCourse(mRealm, (`object` as RealmMyCourse).courseId)
                createMyCourse(myObject, mRealm, model?.id)
                onAdd(mRealm, "courses", profileDbHandler.userModel?.id, myObject?.courseId)
                toast(activity, getString(R.string.added_to_my_courses))
            }
        }
        recyclerView.adapter = getAdapter()
        showNoData(tvMessage, getAdapter().itemCount, "")
    }

    fun deleteSelected(deleteProgress: Boolean) {
        for (i in selectedItems?.indices!!) {
            if (!mRealm.isInTransaction()) mRealm.beginTransaction()
            val `object` = selectedItems?.get(i) as RealmObject
            deleteCourseProgress(deleteProgress, `object`)
            removeFromShelf(`object`)
            recyclerView.adapter = getAdapter()
            showNoData(tvMessage, getAdapter().itemCount, "")
        }
    }

    fun countSelected(): Int {
        return selectedItems?.size ?: 0
    }

    private fun deleteCourseProgress(deleteProgress: Boolean, `object`: RealmObject) {
        if (deleteProgress && `object` is RealmMyCourse) {
            mRealm.where(RealmCourseProgress::class.java).equalTo("courseId", `object`.courseId).findAll().deleteAllFromRealm()
            val examList: List<RealmStepExam> = mRealm.where(RealmStepExam::class.java).equalTo("courseId", `object`.courseId).findAll()
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

    private fun checkAndAddToList(course: RealmMyCourse?, courses: MutableList<RealmMyCourse>, tags: List<RealmTag>) {
        for (tg in tags) {
            val count = mRealm.where(RealmTag::class.java).equalTo("db", "courses").equalTo("tagId", tg.id)
                .equalTo("linkId", course?.courseId).count()
            if (count > 0 && !courses.contains(course)) {
                course?.let { courses.add(it) }
            }
        }
    }

    private fun <LI : RealmModel> getData(s: String, c: Class<LI>): List<LI> {
        val queryParts = s.split(" ").filterNot { it.isEmpty() }
        return if (s.contains(" ")) {
            val data: RealmResults<LI> = mRealm.where(c).findAll()
            data.filter { item ->
                searchAndMatch(item, c, queryParts)
            }
        } else {
            mRealm.where(c).contains(if (c == RealmMyLibrary::class.java) "title" else "courseTitle", s, Case.INSENSITIVE).findAll()
        }
    }

    private fun <LI : RealmModel> searchAndMatch(item: LI, c: Class<out RealmModel>, queryParts: List<String>): Boolean {
        val title = if (c.isAssignableFrom(RealmMyLibrary::class.java)) {
            (item as RealmMyLibrary).title
        } else {
            (item as RealmMyCourse).courseTitle
        }
        return queryParts.all { queryPart ->
            title?.lowercase(Locale.getDefault())?.contains(queryPart.lowercase(Locale.getDefault())) == true
        }
    }

    fun filterLibraryByTag(s: String, tags: List<RealmTag>): List<RealmMyLibrary> {
        var list = getData(s, RealmMyLibrary::class.java)
        list = if (isMyCourseLib) {
            getMyLibraryByUserId(model?.id, list)
        } else {
            getOurLibrary(model?.id, list)
        }
        if (tags.isEmpty()) {
            return list
        }
        val libraries = mutableListOf<RealmMyLibrary>()
        for (library in list) {
            filter(tags, library, libraries)
        }
        return libraries
    }

    fun filterCourseByTag(s: String, tags: List<RealmTag>): List<RealmMyCourse> {
        if (tags.isEmpty() && s.isEmpty()) {
            return applyCourseFilter(filterRealmMyCourseList(getList(RealmMyCourse::class.java)))
        }
        var list = getData(s, RealmMyCourse::class.java)
        list = if (isMyCourseLib) {
            getMyCourseByUserId(model?.id, list)
        } else {
            getOurCourse(model?.id, list)
        }
        if (tags.isEmpty()) {
            return list
        }
        val courses = RealmList<RealmMyCourse>()
        list.forEach { course ->
            checkAndAddToList(course, courses, tags)
        }
        return applyCourseFilter(list)
    }

    private fun filterRealmMyCourseList(items: List<Any?>): List<RealmMyCourse> {
        return items.filterIsInstance<RealmMyCourse>()
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
        if (TextUtils.isEmpty(subjectLevel) && TextUtils.isEmpty(gradeLevel)) return courses
        val newList: MutableList<RealmMyCourse> = ArrayList()
        for (l in courses) {
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
        lateinit var settings: SharedPreferences

        fun showNoData(v: View?, count: Int?, source: String) {
            v ?: return
            v.visibility = if (count == 0) {
                View.VISIBLE
            } else {
                View.GONE
            }
            when (source) {
                "courses" -> (v as TextView).setText(R.string.no_courses)
                "resources" -> (v as TextView).setText(R.string.no_resources)
                "finances" -> (v as TextView).setText(R.string.no_finance_record)
                "news" -> (v as TextView).setText(R.string.no_voices_available)
                "teamCourses" -> (v as TextView).setText(R.string.no_team_courses)
                "teamResources" -> (v as TextView).setText(R.string.no_team_resources)
                "tasks" -> (v as TextView).setText(R.string.no_tasks)
                "members" -> (v as TextView).setText(R.string.no_join_request_available)
                "discussions" -> (v as TextView).setText(R.string.no_news)
                "survey" -> (v as TextView).setText(R.string.no_surveys)
                "submission" -> (v as TextView).setText(R.string.no_submissions)
                "teams" -> (v as TextView).setText(R.string.no_teams)
                "chatHistory" -> (v as TextView).setText(R.string.no_chats)
                "feedback" -> (v as TextView).setText(R.string.no_feedback)
                else -> (v as TextView).setText(R.string.no_data_available_please_check_and_try_again)
            }
        }

        fun showNoFilter(v: View?, count: Int) {
            v ?: return
            v.visibility = if (count == 0) View.VISIBLE else View.GONE
            (v as TextView).setText(R.string.no_course_matched_filter)
        }
    }
}
