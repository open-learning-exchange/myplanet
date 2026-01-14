package org.ole.planet.myplanet.base

import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.realm.RealmList
import io.realm.RealmModel
import io.realm.RealmObject
import io.realm.RealmResults
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.getAllCourses
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.getMyLibraryByUserId
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.getOurLibrary
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.utilities.Utilities.toast

abstract class BaseRecyclerFragment<LI> : BaseRecyclerParentFragment<Any?>(), OnRatingChangeListener {
    var subjects: MutableSet<String> = mutableSetOf()
    var languages: MutableSet<String> = mutableSetOf()
    var mediums: MutableSet<String> = mutableSetOf()
    var levels: MutableSet<String> = mutableSetOf()
    var selectedItems: MutableList<LI>? = null
    var gradeLevel = ""
    var subjectLevel = ""
    lateinit var recyclerView: RecyclerView
    lateinit var tvMessage: TextView
    lateinit var tvFragmentInfo: TextView
    var tvDelete: TextView? = null
    var list: MutableList<LI>? = null
    var resources: List<RealmMyLibrary>? = null
    var courseLib: String? = null
    private var isAddInProgress = false


    abstract fun getLayout(): Int

    abstract fun getAdapter(): RecyclerView.Adapter<*>

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            isMyCourseLib = it.getBoolean("isMyCourseLib")
            courseLib = it.getString("courseLib")
            @Suppress("UNCHECKED_CAST")
            resources = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getSerializable("resources", ArrayList::class.java) as? ArrayList<RealmMyLibrary>
            } else {
                it.getSerializable("resources") as? ArrayList<RealmMyLibrary>
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(getLayout(), container, false)
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
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        viewLifecycleOwner.lifecycleScope.launch {
            mRealm = databaseService.realmInstance
            model = profileDbHandler.userModel
            val adapter = getAdapter()
            recyclerView.adapter = adapter
            if (isMyCourseLib && adapter.itemCount != 0 && courseLib == "courses") {
                resources?.let { showDownloadDialog(it) }
            } else if (isMyCourseLib && courseLib == null && !isSurvey) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val userId = settings.getString("userId", "--")
                    val libraryList = resourcesRepository.getLibraryListForUser(userId)
                    showDownloadDialog(libraryList)
                }
            }
            startPostponedEnterTransition()
            requireActivity().reportFullyDrawn()
        }
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
        if (!isRealmInitialized() || isAddInProgress) return

        val itemsToAdd = selectedItems?.toList() ?: emptyList()
        if (itemsToAdd.isEmpty()) return

        val resourceIds = mutableSetOf<String>()
        val courseIds = mutableSetOf<String>()

        itemsToAdd.forEach { item ->
            val realmObject = item as? RealmObject ?: return@forEach
            when (realmObject) {
                is RealmMyLibrary -> realmObject.resourceId?.let(resourceIds::add)
                is RealmMyCourse -> realmObject.courseId?.let(courseIds::add)
            }
        }

        if (resourceIds.isEmpty() && courseIds.isEmpty()) {
            return
        }

        isAddInProgress = true
        setJoinInProgress(true)

        val userId = profileDbHandler.userModel?.id

        viewLifecycleOwner.lifecycleScope.launch {
            var libraryAdded = false
            var courseAdded = false
            val result = runCatching {
                resourceIds.forEach { resourceId ->
                    if (!userId.isNullOrBlank()) {
                        resourcesRepository.updateUserLibrary(resourceId, userId, isAdd = true)
                        libraryAdded = true
                    }
                }

                courseIds.forEach { courseId ->
                    if (courseId.isNotBlank()) {
                        val added = coursesRepository.markCourseAdded(courseId, userId)
                        courseAdded = courseAdded || added
                    }
                }
            }

            isAddInProgress = false
            setJoinInProgress(false)

            if (!mRealm.isClosed) {
                mRealm.refresh()
            }

            val newAdapter = getAdapter()
            recyclerView.adapter = newAdapter
            showNoData(tvMessage, newAdapter.itemCount, "")

            result.exceptionOrNull()?.let { throw it }

            if (libraryAdded) {
                toast(activity, getString(R.string.added_to_my_library))
            }
            if (courseAdded) {
                toast(activity, getString(R.string.added_to_my_courses))
            }
        }
    }

    private fun setJoinInProgress(inProgress: Boolean) {
        recyclerView.isEnabled = !inProgress
        recyclerView.alpha = if (inProgress) 0.6f else 1f
        view?.findViewById<View>(R.id.tv_add)?.let { addButton ->
            addButton.isEnabled = if (inProgress) {
                false
            } else {
                !(selectedItems.isNullOrEmpty())
            }
            addButton.alpha = if (inProgress) 0.5f else 1f
        }
    }

    fun deleteSelected(deleteProgress: Boolean) {
        selectedItems?.forEach { item ->
            try {
                if (!mRealm.isInTransaction) {
                    mRealm.beginTransaction()
                }
                val `object` = item as RealmObject
                deleteCourseProgress(deleteProgress, `object`)
                removeFromShelf(`object`)
                if (mRealm.isInTransaction) {
                    mRealm.commitTransaction()
                }
            } catch (e: Exception) {
                if (mRealm.isInTransaction) {
                    mRealm.cancelTransaction()
                }
                throw e
            }
        }
        recyclerView.adapter = getAdapter()
        showNoData(tvMessage, getAdapter().itemCount, "")
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
        if (s.isEmpty()) return mRealm.where(c).findAll()

        val queryParts = s.split(" ").filterNot { it.isEmpty() }
        val normalizedQueryParts = queryParts.map { normalizeText(it) }
        val data: RealmResults<LI> = mRealm.where(c).findAll()
        val normalizedQuery = normalizeText(s)
        val startsWithQuery = mutableListOf<LI>()
        val containsQuery = mutableListOf<LI>()

        for (item in data) {
            val title = getTitle(item, c)?.let { normalizeText(it) } ?: continue

            if (title.startsWith(normalizedQuery, ignoreCase = true)) {
                startsWithQuery.add(item)
            } else if (normalizedQueryParts.all { title.contains(it, ignoreCase = true) }) {
                containsQuery.add(item)
            }
        }
        return startsWithQuery + containsQuery
    }

    private fun <LI : RealmModel> getTitle(item: LI, c: Class<LI>): String? {
        return when {
            c.isAssignableFrom(RealmMyLibrary::class.java) -> (item as RealmMyLibrary).title
            else -> (item as RealmMyCourse).courseTitle
        }
    }

    fun filterLibraryByTag(s: String, tags: List<RealmTag>): List<RealmMyLibrary> {
        val normalizedSearchTerm = normalizeText(s)
        var list = getData(s, RealmMyLibrary::class.java)
        list = if (isMyCourseLib) {
            getMyLibraryByUserId(model?.id, list)
        } else {
            getOurLibrary(model?.id, list)
        }

        val libraries = if (tags.isNotEmpty()) {
            val filteredLibraries = mutableListOf<RealmMyLibrary>()
            for (library in list) {
                filter(tags, library, filteredLibraries)
            }
            filteredLibraries
        } else {
            list
        }

        return libraries
    }

    fun normalizeText(str: String): String {
        return Normalizer.normalize(str.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }

    fun filterCourseByTag(s: String, tags: List<RealmTag>): List<RealmMyCourse> {
        if (tags.isEmpty() && s.isEmpty()) {
            return applyCourseFilter(filterRealmMyCourseList(getList(RealmMyCourse::class.java)))
        }
        var list = getData(s, RealmMyCourse::class.java)
        list = if (isMyCourseLib) {
            coursesRepository.getMyCourses(model?.id, list)
        } else {
            getAllCourses(model?.id, list)
        }
        if (tags.isEmpty()) {
            return list
        }
        val courses = RealmList<RealmMyCourse>()
        list.forEach { course ->
            checkAndAddToList(course, courses, tags)
        }
        return applyCourseFilter(courses)
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
        val lev = levels.isEmpty() || l.level?.containsAll(levels) == true
        val lan = languages.isEmpty() || languages.contains(l.language)
        val med = mediums.isEmpty() || mediums.contains(l.mediaType)
        return sub && lev && lan && med
    }

    override fun onDestroy() {
        cleanupRealm()
        super.onDestroy()
    }

    private fun cleanupRealm() {
        if (isRealmInitialized()) {
            try {
                mRealm.removeAllChangeListeners()

                if (mRealm.isInTransaction) {
                    try {
                        mRealm.commitTransaction()
                    } catch (e: Exception) {
                        mRealm.cancelTransaction()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (!mRealm.isClosed) {
                    mRealm.close()
                }
            }
        }
    }

    override fun onDetach() {
        super.onDetach()
        cleanupReferences()
    }

    private fun cleanupReferences() {
        selectedItems?.clear()
        list?.clear()
        selectedItems = null
        list = null
        resources = null
    }

    companion object {
        private val noDataMessages = mapOf(
            "courses" to R.string.no_courses,
            "resources" to R.string.no_resources,
            "finances" to R.string.no_finance_record,
            "news" to R.string.no_voices_available,
            "teamCourses" to R.string.no_team_courses,
            "teamResources" to R.string.no_team_resources,
            "tasks" to R.string.no_tasks,
            "members" to R.string.no_join_request_available,
            "discussions" to R.string.no_news,
            "survey" to R.string.no_surveys,
            "survey_submission" to R.string.no_survey_submissions,
            "exam_submission" to R.string.no_exam_submissions,
            "team" to R.string.no_teams,
            "enterprise" to R.string.no_enterprise,
            "chatHistory" to R.string.no_chats,
            "feedback" to R.string.no_feedback,
            "reports" to R.string.no_reports
        )

        fun showNoData(v: View?, count: Int?, source: String) {
            v ?: return
            v.visibility = if (count == 0) View.VISIBLE else View.GONE
            val messageRes = noDataMessages[source]
                ?: R.string.no_data_available_please_check_and_try_again
            (v as TextView).setText(messageRes)
        }

        fun showNoFilter(v: View?, count: Int) {
            v ?: return
            v.visibility = if (count == 0) View.VISIBLE else View.GONE
            (v as TextView).setText(R.string.no_course_matched_filter)
        }
    }
}
