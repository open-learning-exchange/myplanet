package org.ole.planet.myplanet.ui.course


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
import org.ole.planet.myplanet.model.*
import org.ole.planet.myplanet.service.UserProfileDbHandler

/**
 * A simple [Fragment] subclass.
 */
class MyProgressFragment : Fragment() {
    lateinit var binding: FragmentMyProgressBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentMyProgressBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        var realm = DatabaseService(requireActivity()).realmInstance
        var user = UserProfileDbHandler(requireActivity()).userModel
        var mycourses = RealmMyCourse.getMyCourseByUserId(
            user.getId(),
            realm.where(RealmMyCourse::class.java).findAll()
        )
        var arr = JsonArray()
        var courseProgress = RealmCourseProgress.getCourseProgress(realm, user.id)
        mycourses.forEach {
            var obj = JsonObject()
            obj.addProperty("courseName", it.courseTitle)
            obj.addProperty("courseId", it.courseId)
            obj.add("progress", courseProgress[it.id])
            var submissions = realm.where(RealmSubmission::class.java).equalTo("userId", user.id)
                .contains("parentId", it.courseId).equalTo("type", "exam").findAll()
            var totalMistakes = 0;
            var exams =
                realm.where(RealmStepExam::class.java).equalTo("courseId", it.courseId).findAll()
            var examIds: List<String> = exams.map {
                it.id as String
            }
            submissionMap(submissions, realm, examIds, totalMistakes, obj)
            arr.add(obj)
        }
        binding.rvMyprogress.layoutManager = LinearLayoutManager(requireActivity())
        binding.rvMyprogress.adapter = AdapterMyProgress(requireActivity(), arr)
    }

    private fun submissionMap(
        submissions: RealmResults<RealmSubmission>,
        realm: Realm,
        examIds: List<String>,
        totalMistakes: Int,
        obj: JsonObject
    ) {
        var totalMistakes1 = totalMistakes
        submissions.map {
            var answers =
                realm.where(RealmAnswer::class.java).equalTo("submissionId", it.id).findAll()
            var mistakesMap = HashMap<String, Int>();
            answers.map { r ->
                var question =
                    realm.where(RealmExamQuestion::class.java).equalTo("id", r.questionId)
                        .findFirst()
                if (examIds.contains(question!!.examId)) {
                    totalMistakes1 += r.mistakes
                    if (mistakesMap.containsKey(question!!.examId)) {
                        mistakesMap[examIds.indexOf(question!!.examId).toString()] =
                            mistakesMap[question!!.examId]!!.plus(r.mistakes)
                    } else {
                        mistakesMap[examIds.indexOf(question!!.examId).toString()] = r.mistakes
                    }
                }
            }
            obj.add(
                "stepMistake",
                Gson().fromJson(Gson().toJson(mistakesMap), JsonObject::class.java)
            )
            obj.addProperty("mistakes", totalMistakes1)
        }
    }

}
