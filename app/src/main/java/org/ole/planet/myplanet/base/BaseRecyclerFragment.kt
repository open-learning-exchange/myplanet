package org.ole.planet.myplanet.base

import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.*
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            isMyCourseLib = it.getBoolean("isMyCourseLib")
            courseLib = it.getString("courseLib")
            val json = it.getString("resources")
            resources = json?.let {
                val type = object : TypeToken<ArrayList<RealmMyLibrary>>() {}.type
                Gson().fromJson<ArrayList<RealmMyLibrary>>(json, type)
            } ?: arrayListOf()
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
            it.setOnClickListener {
                lifecycleScope.launch {
                    deleteSelected(false)
                }
            }
        }
    }

    override fun onRatingChanged() {
        recyclerView.adapter = getAdapter()
    }

    suspend fun addToMyList() {
        for (i in selectedItems?.indices!!) {
            val `object` = selectedItems?.get(i) as RealmObject
            if (`object` is RealmMyLibrary) {
                val myObject = mRealm.query<RealmMyLibrary>("resourceId == $0", `object`.resourceId).first().find()
                RealmMyLibrary.createFromResource(myObject, mRealm, model?.id)
                RealmRemovedLog.onAdd(mRealm, "resources", profileDbHandler.userModel?.id, myObject?.resourceId)
                toast(activity, getString(R.string.added_to_my_library))
            } else {
                val myObject = RealmMyCourse.getMyCourse(mRealm, (`object` as RealmMyCourse).courseId)
                RealmMyCourse.createMyCourse(myObject, mRealm, model?.id)
                RealmRemovedLog.onAdd(mRealm, "courses", profileDbHandler.userModel?.id, myObject?.courseId)
                toast(activity, getString(R.string.added_to_my_courses))
            }
        }
        recyclerView.adapter = getAdapter()
        showNoData(tvMessage, getAdapter().itemCount, "")
    }

    suspend fun deleteSelected(deleteProgress: Boolean) {
        withContext(Dispatchers.IO) {
            for (i in selectedItems?.indices!!) {
                val `object` = selectedItems?.get(i) as RealmObject
                if (deleteProgress && `object` is RealmMyCourse) {
                    val courseProgress = mRealm.query<RealmCourseProgress>("courseId == $0", `object`.courseId).find()
                    val examList = mRealm.query<RealmStepExam>("courseId == $0", `object`.courseId).find()
                    val submissionsToDelete = examList.flatMap { exam ->
                        mRealm.query<RealmSubmission>("parentId == $0 AND type != $1 AND uploaded == $2", exam.id, "survey", false).find()
                    }

                    mRealm.write {
                        courseProgress.forEach { delete(it) }
                        submissionsToDelete.forEach { delete(it) }
                    }
                }

                lifecycleScope.launch {
                    removeFromShelf(`object`)
                }
            }
        }

        withContext(Dispatchers.Main) {
            recyclerView.adapter = getAdapter()
            showNoData(tvMessage, getAdapter().itemCount, "")
        }
    }

    fun countSelected(): Int {
        return selectedItems?.size ?: 0
    }

    override fun onDestroy() {
        super.onDestroy()
        mRealm.close()
    }

    private fun checkAndAddToList(course: RealmMyCourse?, courses: MutableList<RealmMyCourse>, tags: List<RealmTag>) {
        for (tg in tags) {
            val count = mRealm.query<RealmTag>("db == $0 AND tagId == $1 AND linkId == $2", "courses", tg.id, course?.courseId).count().find()
            if (count > 0 && !courses.contains(course)) {
                course?.let { courses.add(it) }
            }
        }
    }

    private inline fun <reified LI : RealmObject> getData(s: String, c: Class<LI>): List<LI> {
        val queryParts = s.split(" ").filterNot { it.isEmpty() }
        return if (s.contains(" ")) {
            val data = mRealm.query<LI>().find()
            data.filter { item ->
                searchAndMatch(item, queryParts)
            }
        } else {
            val field = if (c == RealmMyLibrary::class.java) "title" else "courseTitle"
            mRealm.query<LI>("$field CONTAINS[c] $0", s).find()
        }
    }

    private fun <LI : RealmObject> searchAndMatch(item: LI, queryParts: List<String>): Boolean {
        val title = when (item) {
            is RealmMyLibrary -> item.title
            is RealmMyCourse -> item.courseTitle
            else -> null
        }
        return queryParts.all { queryPart ->
            title?.lowercase(Locale.getDefault())?.contains(queryPart.lowercase(Locale.getDefault())) == true
        }
    }

    fun filterLibraryByTag(s: String, tags: List<RealmTag>): List<RealmMyLibrary> {
        var list = getData(s, RealmMyLibrary::class.java)
        list = if (isMyCourseLib) {
            RealmMyLibrary.getMyLibraryByUserId(model?.id, list)
        } else {
            RealmMyLibrary.getOurLibrary(model?.id, list)
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
            RealmMyCourse.getMyCourseByUserId(model?.id ?: "", list)
        } else {
            RealmMyCourse.getOurCourse(model?.id ?: "", list)
        }
        if (tags.isEmpty()) {
            return list
        }
        val courses = mutableListOf<RealmMyCourse>()
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
            val count = mRealm.query<RealmTag>("db == $0 AND tagId == $1 AND linkId == $2", "resources", tg.id, library?.id).count().find()
            if (count > 0 && !libraries.contains(library)) {
                library?.let { libraries.add(it) }
            }
        }
    }

    fun applyFilter(libraries: List<RealmMyLibrary>): List<RealmMyLibrary> {
        return libraries.filter { isValidFilter(it) }
    }

    private fun applyCourseFilter(courses: List<RealmMyCourse>): List<RealmMyCourse> {
        if (TextUtils.isEmpty(subjectLevel) && TextUtils.isEmpty(gradeLevel)) return courses
        return courses.filter { course ->
            TextUtils.equals(course.gradeLevel, gradeLevel) || TextUtils.equals(course.subjectLevel, subjectLevel)
        }
    }

    private fun isValidFilter(l: RealmMyLibrary): Boolean {
        val sub = subjects.isEmpty() || subjects.let { l.subject.containsAll(it) } == true
        val lev = levels.isEmpty() || l.level.containsAll(levels)
        val lan = languages.isEmpty() || languages.contains(l.language)
        val med = mediums.isEmpty() || mediums.contains(l.mediaType)
        return sub && lev && lan && med
    }

    companion object {
        lateinit var settings: SharedPreferences

        fun showNoData(v: View?, count: Int?, source: String) {
            v ?: return
            v.visibility = if (count == 0) View.VISIBLE else View.GONE
            (v as TextView).setText(when (source) {
                "courses" -> R.string.no_courses
                "resources" -> R.string.no_resources
                "finances" -> R.string.no_finance_record
                "news" -> R.string.no_voices_available
                "teamCourses" -> R.string.no_team_courses
                "teamResources" -> R.string.no_team_resources
                "tasks" -> R.string.no_tasks
                "members" -> R.string.no_join_request_available
                "discussions" -> R.string.no_news
                "survey" -> R.string.no_surveys
                "submission" -> R.string.no_submissions
                "teams" -> R.string.no_teams
                "chatHistory" -> R.string.no_chats
                "feedback" -> R.string.no_feedback
                else -> R.string.no_data_available_please_check_and_try_again
            })
        }

        fun showNoFilter(v: View?, count: Int) {
            v ?: return
            v.visibility = if (count == 0) View.VISIBLE else View.GONE
            (v as TextView).setText(R.string.no_course_matched_filter)
        }
    }
}