package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import io.realm.RealmResults
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.databinding.ActivityCourseProgressBinding
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileService
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils

@AndroidEntryPoint
class CourseProgressActivity : BaseActivity() {
    private lateinit var binding: ActivityCourseProgressBinding
    @Inject
    lateinit var userProfileDbHandler: UserProfileService
    var user: RealmUserModel? = null
    lateinit var courseId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCourseProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtils.setupEdgeToEdge(this, binding.root)
        initActionBar()
        courseId = intent.getStringExtra("courseId").toString()
        user = userProfileDbHandler.userModel

        binding.rvProgress.layoutManager = GridLayoutManager(this, 4)

        lifecycleScope.launch {
            val data = loadData(courseId, user?.id)
            if (data != null) {
                updateUI(data)
            }
        }
    }

    private fun updateUI(data: CourseProgressData) {
        if (data.max != 0) {
            binding.progressView.setProgress((data.current.toDouble() / data.max.toDouble() * 100).toInt(), true)
        } else {
            binding.progressView.setProgress(0, true)
        }
        binding.tvCourse.text = data.title
        binding.tvProgress.text = getString(
            R.string.course_progress,
            data.current.toString(),
            data.max.toString()
        )
        val adapter = ProgressGridAdapter(this)
        binding.rvProgress.adapter = adapter
        adapter.submitList(data.steps.map { it.asJsonObject })
    }

    private suspend fun loadData(courseId: String, userId: String?): CourseProgressData? {
        return withContext(Dispatchers.IO) {
            databaseService.withRealm { realm ->
                val stepsList = RealmMyCourse.getCourseSteps(realm, courseId)
                val max = stepsList.size
                val current = RealmCourseProgress.getCurrentProgress(stepsList, realm, userId, courseId)

                val course = realm.where(RealmMyCourse::class.java).equalTo("courseId", courseId).findFirst()
                val title = course?.courseTitle

                val array = JsonArray()
                stepsList.forEach { step ->
                    val ob = JsonObject()
                    ob.addProperty("stepId", step.id)
                    val exams = realm.where(RealmStepExam::class.java).equalTo("stepId", step.id).findAll()
                    getExamObject(realm, exams, ob, userId)
                    array.add(ob)
                }
                CourseProgressData(title, current, max, array)
            }
        }
    }

    private fun getExamObject(
        realm: Realm,
        exams: RealmResults<RealmStepExam>,
        ob: JsonObject,
        userId: String?
    ) {
        exams.forEach { it ->
            it.id?.let { it1 ->
                realm.where(RealmSubmission::class.java).equalTo("userId", userId)
                    .contains("parentId", it1).equalTo("type", "exam").findAll()
            }?.map {
                val answers = realm.where(RealmAnswer::class.java).equalTo("submissionId", it.id).findAll()
                var examId = it.parentId
                if (it.parentId?.contains("@") == true) {
                    examId = it.parentId!!.split("@")[0]
                }
                val questions = realm.where(RealmExamQuestion::class.java).equalTo("examId", examId).findAll()
                val questionCount = questions.size
                if (questionCount == 0) {
                    ob.addProperty("completed", false)
                    ob.addProperty("percentage", 0)
                } else {
                    ob.addProperty("completed", answers.size == questionCount)
                    val percentage = (answers.size.toDouble() / questionCount) * 100
                    ob.addProperty("percentage", percentage)
                }
                ob.addProperty("status", it.status)
            }
        }
    }
}

data class CourseProgressData(
    val title: String?,
    val current: Int,
    val max: Int,
    val steps: JsonArray
)
