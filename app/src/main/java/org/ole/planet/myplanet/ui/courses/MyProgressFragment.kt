package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.*
import io.realm.kotlin.Realm
import io.realm.kotlin.query.RealmResults
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.databinding.FragmentMyProgressBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.*
import org.ole.planet.myplanet.service.UserProfileDbHandler

class MyProgressFragment : Fragment() {
    private lateinit var fragmentMyProgressBinding: FragmentMyProgressBinding
    private val scope = MainApplication.applicationScope

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentMyProgressBinding = FragmentMyProgressBinding.inflate(inflater, container, false)
        return fragmentMyProgressBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeData()
    }

    private fun initializeData() {
        val realm = DatabaseService().realmInstance
        val user = UserProfileDbHandler(requireActivity()).userModel
        scope.launch {
            val courseData = fetchCourseData(realm, user?.id ?: "")
            fragmentMyProgressBinding.rvMyprogress.apply {
                layoutManager = LinearLayoutManager(requireActivity())
                adapter = AdapterMyProgress(requireActivity(), courseData)
            }
        }
    }

    companion object {
        suspend fun fetchCourseData(realm: Realm, userId: String): JsonArray {
            val mycourses = realm.query<RealmMyCourse>(RealmMyCourse::class).find().let { RealmMyCourse.getMyCourseByUserId(userId, it) }

            val arr = JsonArray()
            val courseProgress = RealmCourseProgress.getCourseProgress(realm, userId).first()

            mycourses.forEach { course ->
                val obj = JsonObject().apply {
                    addProperty("courseName", course.courseTitle)
                    addProperty("courseId", course.courseId)
                    add("progress", courseProgress[course.id])
                }

                course.courseId.let { courseId ->
                    val submissions = realm.query<RealmSubmission>(RealmSubmission::class,
                        "userId == $0 AND parentId CONTAINS $1 AND type == $2", userId, courseId, "exam"
                    ).find()

                    val exams = realm.query<RealmStepExam>(RealmStepExam::class, "courseId == $0", courseId).find()
                    val examIds = exams.map { it.id as String }

                    submissionMap(submissions, realm, examIds, obj)
                }
                arr.add(obj)
            }
            return arr
        }

        private fun submissionMap(submissions: RealmResults<RealmSubmission>, realm: Realm, examIds: List<String>, obj: JsonObject) {
            var totalMistakes = 0
            val mistakesMap = HashMap<String, Int>()

            submissions.forEach { submission ->
                val answers = realm.query<RealmAnswer>(RealmAnswer::class, "submissionId == $0", submission.id).find()

                answers.forEach { answer ->
                    realm.query<RealmExamQuestion>(RealmExamQuestion::class, "id == $0", answer.questionId)
                        .first().find()?.let { question ->
                            if (examIds.contains(question.examId)) {
                                totalMistakes += answer.mistakes
                                val examIndex = examIds.indexOf(question.examId).toString()
                                mistakesMap[examIndex] = mistakesMap.getOrDefault(examIndex, 0) + answer.mistakes
                            }
                        }
                }
            }

            obj.apply {
                add("stepMistake", Gson().fromJson(Gson().toJson(mistakesMap), JsonObject::class.java))
                addProperty("mistakes", totalMistakes)
            }
        }

        fun countUsersWhoCompletedCourse(realm: Realm, courseId: String): Int {
            var completedCount = 0
            val allUsers = realm.query<RealmUserModel>(RealmUserModel::class).find()

            allUsers.forEach { user ->
                val userId = user.id
                val courses = realm.query<RealmMyCourse>(RealmMyCourse::class).find()
                    .let { RealmMyCourse.getMyCourseByUserId(userId, it) }

                courses.find { it.courseId == courseId }?.let {
                    val steps = RealmMyCourse.getCourseSteps(realm, courseId)
                    val currentProgress = RealmCourseProgress.getCurrentProgress(steps, realm, userId, courseId)
                    if (currentProgress == steps.size) completedCount++
                }
            }
            return completedCount
        }
    }
}
