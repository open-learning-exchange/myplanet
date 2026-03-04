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
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.utils.Utilities.toast

abstract class BaseRecyclerFragment<LI> : BaseRecyclerParentFragment<Any?>(), OnRatingChangeListener {
    var selectedItems: MutableList<LI>? = null
    var gradeLevel = ""
    var subjectLevel = ""
    lateinit var recyclerView: RecyclerView
    lateinit var tvMessage: TextView
    lateinit var tvFragmentInfo: TextView
    var tvDelete: TextView? = null
    var list: MutableList<LI>? = null
    private var isAddInProgress = false

    abstract fun getLayout(): Int

    abstract suspend fun getAdapter(): RecyclerView.Adapter<*>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            isMyCourseLib = it.getBoolean("isMyCourseLib")
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
            mRealm = databaseService.createManagedRealmInstance()
            model = profileDbHandler.getUserModel()
            val adapter = getAdapter()
            recyclerView.adapter = adapter
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

            val result = runCatching {
                if (resourceIds.isNotEmpty()) {
                    resourcesRepository.addResourcesToUserLibrary(resourceIds, userId)
                    libraryAdded = true
                }

                if (courseIds.isNotEmpty()) {
                    courseIds.forEach { courseId ->
                        if (coursesRepository.markCourseAdded(courseId, userId)) {
                            courseAdded = true
                        }
                    }
                }
            }

            isAddInProgress = false
            setJoinInProgress(false)

            if (view == null || !isAdded || requireActivity().isFinishing) return@launch

            if (!mRealm.isClosed) {
                mRealm.refresh()
            }

            val newAdapter = getAdapter()
            recyclerView.adapter = newAdapter
            showNoData(tvMessage, newAdapter.itemCount, "")

            result.exceptionOrNull()?.let {
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

    open fun deleteSelected(deleteProgress: Boolean) {
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
        selectedItems?.clear()
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

    fun normalizeText(str: String): String {
        return Normalizer.normalize(str.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
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
