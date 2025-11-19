package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import android.os.Trace
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import io.realm.RealmResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import org.ole.planet.myplanet.databinding.FragmentMyProgressBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.service.UserProfileDbHandler

@AndroidEntryPoint
class MyProgressFragment : Fragment() {
    private var _binding: FragmentMyProgressBinding? = null
    private val binding get() = _binding!!
    @Inject
    lateinit var databaseService: DatabaseService
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    private var progressJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMyProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeData()
    }

    override fun onStop() {
        super.onStop()
        progressJob?.cancel()
    }

    private fun initializeData() {
        progressJob = viewLifecycleOwner.lifecycleScope.launch {
            val courseData = withContext(Dispatchers.IO) {
                Trace.beginSection("fetchCourseProgress")
                try {
                    databaseService.withRealm { realm ->
                        val user = userProfileDbHandler.userModel
                        fetchCourseData(realm, user?.id)
                    }
                } finally {
                    Trace.endSection()
                }
            }
            binding.rvMyprogress.layoutManager = LinearLayoutManager(requireActivity())
            binding.rvMyprogress.adapter = AdapterMyProgress(requireActivity(), courseData)
        }
    }

    companion object {
        fun fetchCourseData(realm: Realm, userId: String?): List<CourseProgress> {
            val mycourses = RealmMyCourse.getMyCourseByUserId(
                userId,
                realm.where(RealmMyCourse::class.java).findAll()
            )
            val courseProgress = RealmCourseProgress.getCourseProgress(realm, userId)
            return mycourses.map { course ->
                val progress = courseProgress[course.id]?.asJsonObject?.let {
                    mapOf(
                        "current" to it["current"].asInt,
                        "max" to it["max"].asInt
                    )
                }

                val submissions = course.courseId?.let { courseId ->
                    realm.where(RealmSubmission::class.java)
                        .equalTo("userId", userId)
                        .contains("parentId", courseId)
                        .equalTo("type", "exam")
                        .findAll()
                }
                val exams = realm.where(RealmStepExam::class.java)
                    .equalTo("courseId", course.courseId)
                    .findAll()
                val examIds: List<String> = exams.map { it.id as String }

                var totalMistakes = 0
                val stepMistakes = mutableMapOf<String, Int>()
                if (submissions != null) {
                    val (mistakes, steps) = submissionMap(submissions, realm, examIds)
                    totalMistakes = mistakes
                    stepMistakes.putAll(steps)
                }

                CourseProgress(
                    courseName = course.courseTitle,
                    courseId = course.courseId,
                    progress = progress,
                    mistakes = totalMistakes,
                    stepMistake = stepMistakes
                )
            }
        }

        private fun submissionMap(
            submissions: RealmResults<RealmSubmission>,
            realm: Realm,
            examIds: List<String>
        ): Pair<Int, Map<String, Int>> {
            var totalMistakes = 0
            val mistakesMap = HashMap<String, Int>()
            submissions.forEach {
                val answers = realm.where(RealmAnswer::class.java)
                    .equalTo("submissionId", it.id)
                    .findAll()
                answers.forEach { r ->
                    val question = realm.where(RealmExamQuestion::class.java)
                        .equalTo("id", r.questionId)
                        .findFirst()
                    if (examIds.contains(question?.examId)) {
                        totalMistakes += r.mistakes
                        question?.examId?.let { examId ->
                            val examIndex = examIds.indexOf(examId).toString()
                            mistakesMap[examIndex] = (mistakesMap[examIndex] ?: 0) + r.mistakes
                        }
                    }
                }
            }
            return Pair(totalMistakes, mistakesMap)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
