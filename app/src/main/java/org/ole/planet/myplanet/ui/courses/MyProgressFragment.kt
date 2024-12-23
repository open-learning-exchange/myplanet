package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmResults
import org.ole.planet.myplanet.databinding.FragmentMyProgressBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler

class MyProgressFragment : Fragment() {
    private lateinit var fragmentMyProgressBinding: FragmentMyProgressBinding

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
        val courseData = fetchCourseData(realm, user?.id)
        fragmentMyProgressBinding.rvMyprogress.layoutManager = LinearLayoutManager(requireActivity())
        fragmentMyProgressBinding.rvMyprogress.adapter = AdapterMyProgress(requireActivity(), courseData)
    }

    companion object {
        fun fetchCourseData(realm: Realm, userId: String?): JsonArray {
            val mycourses = RealmMyCourse.getMyCourseByUserId(
                userId,
                realm.where(RealmMyCourse::class.java).findAll()
            )
            val arr = JsonArray()
            val courseProgress = RealmCourseProgress.getCourseProgress(realm, userId)

            mycourses.forEach { course ->
                val obj = JsonObject()
                obj.addProperty("courseName", course.courseTitle)
                obj.addProperty("courseId", course.courseId)
                obj.add("progress", courseProgress[course.id])

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

                if (submissions != null) {
                    submissionMap(submissions, realm, examIds, obj)
                }
                arr.add(obj)
            }
            return arr
        }

        private fun submissionMap(submissions: RealmResults<RealmSubmission>, realm: Realm, examIds: List<String>, obj: JsonObject) {
            var totalMistakes = 0
            submissions.forEach {
                val answers = realm.where(RealmAnswer::class.java)
                    .equalTo("submissionId", it.id)
                    .findAll()
                val mistakesMap = HashMap<String, Int>()
                answers.forEach { r ->
                    val question = realm.where(RealmExamQuestion::class.java)
                        .equalTo("id", r.questionId)
                        .findFirst()
                    if (examIds.contains(question?.examId)) {
                        totalMistakes += r.mistakes
                        if (mistakesMap.containsKey(question?.examId)) {
                            mistakesMap["${examIds.indexOf(question?.examId)}"] = mistakesMap[question?.examId]!!.plus(r.mistakes)
                        } else {
                            mistakesMap["${examIds.indexOf(question?.examId)}"] = r.mistakes
                        }
                    }
                }
                obj.add("stepMistake", Gson().fromJson(Gson().toJson(mistakesMap), JsonObject::class.java))
                obj.addProperty("mistakes", totalMistakes)
            }
        }

        fun getCourseProgress(courseData: JsonArray, courseId: String): JsonObject? {
            courseData.forEach { element ->
                val course = element.asJsonObject
                if (course.get("courseId").asString == courseId) {
                    return course.getAsJsonObject("progress")
                }
            }
            return null
        }

        fun countUsersWhoCompletedCourse(realm: Realm, courseId: String): Int {
            var completedCount = 0
            val allUsers = realm.where(RealmUserModel::class.java).findAll()

            allUsers.forEach { user ->
                val userId = user.id
                val courses = RealmMyCourse.getMyCourseByUserId(userId, realm.where(RealmMyCourse::class.java).findAll())

                val course = courses.find { it.courseId == courseId }
                if (course != null) {
                    val steps = RealmMyCourse.getCourseSteps(realm, courseId)
                    val currentProgress = RealmCourseProgress.getCurrentProgress(steps, realm, userId, courseId)

                    if (currentProgress == steps.size) {
                        completedCount++
                    }
                }
            }
            return completedCount
        }
    }
}
