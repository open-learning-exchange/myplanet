package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler

data class CourseProgressData(
    val courseTitle: String?,
    val progressText: String,
    val progressPercentage: Int,
    val progressData: JsonArray
)

@AndroidEntryPoint
class CourseProgressActivity : BaseActivity() {
    private lateinit var binding: ActivityCourseProgressBinding
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    private var user: RealmUserModel? = null
    private lateinit var courseId: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCourseProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initActionBar()
        postponeEnterTransition()
        courseId = intent.getStringExtra("courseId").toString()
        user = userProfileDbHandler.userModel
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val data = fetchData()
            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                binding.tvCourse.text = data.courseTitle
                binding.progressView.setProgress(data.progressPercentage, true)
                binding.tvProgress.text = data.progressText
                binding.rvProgress.layoutManager = GridLayoutManager(this@CourseProgressActivity, 4)
                binding.rvProgress.adapter = AdapterProgressGrid(this@CourseProgressActivity, data.progressData)
                startPostponedEnterTransition()
            }
        }
    }

    private suspend fun fetchData(): CourseProgressData = withContext(Dispatchers.IO) {
        databaseService.withRealm { realm ->
            val courseProgress = RealmCourseProgress.getCourseProgress(realm, user?.id)
            val progress = courseProgress[courseId]
            val course = realm.where(RealmMyCourse::class.java).equalTo("courseId", courseId).findFirst()
            val progressPercentage = if (progress != null) {
                val maxProgress = progress["max"].asInt
                if (maxProgress != 0) {
                    (progress["current"].asInt.toDouble() / maxProgress.toDouble() * 100).toInt()
                } else {
                    0
                }
            } else {
                0
            }
            val progressText = getString(
                R.string.course_progress,
                courseProgress[courseId]?.get("current")?.asString,
                courseProgress[courseId]?.get("max")?.asString
            )
            val progressData = showProgress(realm)
            CourseProgressData(course?.courseTitle, progressText, progressPercentage, progressData)
        }
    }

    private fun showProgress(realm: Realm): JsonArray {
        val steps = realm.where(RealmCourseStep::class.java).contains("courseId", courseId).findAll()
        val array = JsonArray()
        steps.forEach {
            val ob = JsonObject()
            ob.addProperty("stepId", it.id)
            val exams = realm.where(RealmStepExam::class.java).equalTo("stepId", it.id).findAll()
            getExamObject(realm, exams, ob)
            array.add(ob)
        }
        return array
    }

    private fun getExamObject(
        realm: Realm,
        exams: RealmResults<RealmStepExam>,
        ob: JsonObject
    ) {
        exams.forEach { stepExam ->
            stepExam.id?.let { examId ->
                realm.where(RealmSubmission::class.java)
                    .equalTo("userId", user?.id)
                    .contains("parentId", examId)
                    .equalTo("type", "exam")
                    .findAll()
            }?.forEach { submission ->
                val answers = realm.where(RealmAnswer::class.java).equalTo("submissionId", submission.id).findAll()
                val parentId = submission.parentId?.split("@")?.firstOrNull()
                val questions = realm.where(RealmExamQuestion::class.java).equalTo("examId", parentId).findAll()
                val questionCount = questions.size
                if (questionCount == 0) {
                    ob.addProperty("completed", false)
                    ob.addProperty("percentage", 0)
                } else {
                    ob.addProperty("completed", answers.size == questionCount)
                    val percentage = (answers.size.toDouble() / questionCount) * 100
                    ob.addProperty("percentage", percentage)
                }
                ob.addProperty("status", submission.status)
            }
        }
    }
}
