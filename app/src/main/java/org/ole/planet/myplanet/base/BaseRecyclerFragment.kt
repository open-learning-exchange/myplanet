package org.ole.planet.myplanet.base

import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.realm.RealmList
import io.realm.RealmObject
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.utils.Utilities.toast

abstract class BaseRecyclerFragment<LI> : BaseRecyclerParentFragment<Any?>(), OnRatingChangeListener {
    var subjects: MutableSet<String> = mutableSetOf()
    var languages: MutableSet<String> = mutableSetOf()
    var mediums: MutableSet<String> = mutableSetOf()
    var levels: MutableSet<String> = mutableSetOf()
    var selectedItems: MutableList<LI>? = null
    var gradeLevel = ""
    var subjectLevel = ""
    lateinit var recyclerView: RecyclerView
    var tvMessage: View? = null
    lateinit var tvFragmentInfo: TextView
    var tvDelete: TextView? = null
    var list: MutableList<LI>? = null
    var resources: List<RealmMyLibrary>? = null
    var courseLib: String? = null
    private var isAddInProgress = false


    abstract fun getLayout(): Int

    abstract suspend fun getAdapter(): RecyclerView.Adapter<out RecyclerView.ViewHolder>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            isMyCourseLib = it.getBoolean("isMyCourseLib")
            courseLib = it.getString("courseLib")
            @Suppress("UNCHECKED_CAST")
            resources = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getSerializable("resources", ArrayList::class.java) as? ArrayList<RealmMyLibrary>
            } else {
                @Suppress("DEPRECATION")
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
            requireRealmInstance()
            model = profileDbHandler.getUserModel()
            val adapter = getAdapter()
            recyclerView.adapter = adapter
            if (isMyCourseLib && adapter.itemCount != 0 && courseLib == "courses") {
                resources?.let { showDownloadDialog(it) }
            } else if (isMyCourseLib && courseLib == null && !isSurvey) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val userId = sharedPrefManager.getUserId().ifEmpty { "--" }
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
            it.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    deleteSelected(false)
                }
            }
        }
    }

    override fun onRatingChanged() {
        viewLifecycleOwner.lifecycleScope.launch {
            recyclerView.adapter = getAdapter()
        }
    }

    open fun addToMyList() {
        if (!isRealmInitialized() || isAddInProgress) return

        val itemsToAdd = selectedItems?.toList() ?: emptyList()
        if (itemsToAdd.isEmpty()) return

        val resourceIds = mutableListOf<String>()
        val courseIds = mutableListOf<String>()

        itemsToAdd.forEach { item ->
            when (val realmObject = item as? RealmObject) {
                is RealmMyLibrary -> realmObject.resourceId?.let(resourceIds::add)
                is RealmMyCourse -> realmObject.courseId?.let(courseIds::add)
                else -> {}
            }
        }

        if (resourceIds.isEmpty() && courseIds.isEmpty()) return

        isAddInProgress = true
        setJoinInProgress(true)

        viewLifecycleOwner.lifecycleScope.launch {
            val userId = profileDbHandler.getUserModel()?.id ?: return@launch
            var libraryAdded = false
            var courseAdded = false
            var errorOccurred: Throwable? = null

            if (resourceIds.isNotEmpty()) {
                val libraryResult = resourcesRepository.addResourcesToUserLibrary(resourceIds, userId)
                libraryResult.onSuccess {
                    libraryAdded = true
                }.onFailure {
                    errorOccurred = it
                }
            }

            if (courseIds.isNotEmpty()) {
                courseIds.forEach { courseId ->
                    val courseResult = coursesRepository.markCourseAdded(courseId, userId)
                    courseResult.onSuccess { added ->
                        if (added) {
                            courseAdded = true
                        }
                    }.onFailure {
                        errorOccurred = it
                    }
                }
            }

            isAddInProgress = false
            setJoinInProgress(false)

            if (view == null || !isAdded || requireActivity().isFinishing) return@launch

            if (!requireRealmInstance().isClosed) {
                requireRealmInstance().refresh()
            }

            val newAdapter = getAdapter()
            recyclerView.adapter = newAdapter
            showNoData(tvMessage, newAdapter.itemCount, "")

            errorOccurred?.let {
                it.printStackTrace()
                toast(activity, "An error occurred: ${it.message}")
                return@launch
            }

            if (libraryAdded) toast(activity, getString(R.string.added_to_my_library))
            if (courseAdded) toast(activity, getString(R.string.added_to_my_courses))
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

    open suspend fun deleteSelected(deleteProgress: Boolean) {
        selectedItems?.forEachIndexed { _, item ->
            val `object` = item as RealmObject
            deleteCourseProgress(deleteProgress, `object`)
            removeFromShelf(`object`)
        }
        selectedItems?.clear()
    }

    fun countSelected(): Int {
        return selectedItems?.size ?: 0
    }

    private suspend fun deleteCourseProgress(deleteProgress: Boolean, `object`: RealmObject) {
        if (deleteProgress && `object` is RealmMyCourse) {
            coursesRepository.deleteCourseProgress(`object`.courseId)
        }
    }

    fun normalizeText(str: String): String {
        return Normalizer.normalize(str.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")
    }

    suspend fun filterCourseByTag(s: String, tags: List<RealmTag>): List<RealmMyCourse> {
        if (tags.isEmpty() && s.isEmpty()) {
            return applyCourseFilter(filterRealmMyCourseList(getList(RealmMyCourse::class.java) as List<RealmMyCourse>))
        }
        var list = coursesRepository.search(s)
        list = if (isMyCourseLib) {
            coursesRepository.getMyCourses(model?.id, list)
        } else {
            coursesRepository.getAllCourses(model?.id, list)
        }
        if (tags.isEmpty()) {
            return list
        }

        val tagIds = tags.mapNotNull { it.id }.toTypedArray()
        val linkedCourseIds = requireRealmInstance().where(RealmTag::class.java)
            .equalTo("db", "courses")
            .`in`("tagId", tagIds)
            .findAll()
            .mapNotNull { it.linkId }
            .toSet()

        val courses = RealmList<RealmMyCourse>()
        list.forEach { course ->
            if (linkedCourseIds.contains(course.courseId) && !courses.contains(course)) {
                courses.add(course)
            }
        }
        return applyCourseFilter(courses)
    }

    private fun filterRealmMyCourseList(items: List<Any?>): List<RealmMyCourse> {
        return items.filterIsInstance<RealmMyCourse>()
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
        private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")

        private val noDataMessages = mapOf(
            "courses" to R.string.no_courses,
            "resources" to R.string.no_resources,
            "resources_filtered" to R.string.no_results_for_filter,
            "finances" to R.string.no_finance_record,
            "news" to R.string.no_voices_available,
            "news_filtered" to R.string.no_results_for_filter,
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
            val textView = if (v is TextView) v else v.findViewById<TextView>(R.id.tv_empty_message)
            textView?.setText(messageRes)
        }

        fun showNoFilter(v: View?, count: Int) {
            v ?: return
            v.visibility = if (count == 0) View.VISIBLE else View.GONE
            val textView = if (v is TextView) v else v.findViewById<TextView>(R.id.tv_empty_message)
            textView?.setText(R.string.no_course_matched_filter)
        }
    }
}
