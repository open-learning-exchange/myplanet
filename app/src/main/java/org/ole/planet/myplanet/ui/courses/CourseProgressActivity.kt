package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import android.os.Trace
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.BuildConfig
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
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils

data class StepProgress(
    val stepId: String?,
    val completed: Boolean?,
    val percentage: Double?,
    val status: String?
)

@AndroidEntryPoint
class CourseProgressActivity : BaseActivity() {
    private lateinit var binding: ActivityCourseProgressBinding
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
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
        val adapter = AdapterProgressGrid(this, mutableListOf())
        binding.rvProgress.adapter = adapter

        lifecycleScope.launch {
            loadCourseProgress(adapter)
        }
    }

    private suspend fun loadCourseProgress(adapter: AdapterProgressGrid) {
        if (BuildConfig.DEBUG) {
            Trace.beginSection("loadCourseProgress")
        }
        try {
            val courseData = withContext(Dispatchers.IO) {
                databaseService.withRealm { realm ->
                    val courseProgress = RealmCourseProgress.getCourseProgress(realm, user?.id)
                    val progress = courseProgress[courseId]
                    val course = realm.where(RealmMyCourse::class.java).equalTo("courseId", courseId).findFirst()
                    Triple(course?.courseTitle, progress, realm.where(RealmCourseStep::class.java).contains("courseId", courseId).count())
                }
            }

            binding.tvCourse.text = courseData.first
            val progress = courseData.second
            if (progress != null) {
                val maxProgress = progress["max"].asInt
                if (maxProgress != 0) {
                    binding.progressView.setProgress((progress["current"].asInt.toDouble() / maxProgress.toDouble() * 100).toInt(), true)
                } else {
                    binding.progressView.setProgress(0, true)
                }
                binding.tvProgress.text = getString(
                    R.string.course_progress,
                    progress["current"]?.asString,
                    progress["max"]?.asString
                )
            }

            val stepCount = courseData.third
            val chunkSize = 10
            var offset = 0
            while (offset < stepCount) {
                val stepProgressList = withContext(Dispatchers.IO) {
                    databaseService.withRealm { realm ->
                        val steps = realm.where(RealmCourseStep::class.java).contains("courseId", courseId).findAll().subList(offset, minOf(offset + chunkSize, stepCount.toInt()))
                        steps.map { step ->
                            getStepProgress(realm, step)
                        }
                    }
                }
                adapter.addSteps(stepProgressList)
                offset += chunkSize
            }
        } finally {
            if (BuildConfig.DEBUG) {
                Trace.endSection()
            }
        }
    }

    private fun getStepProgress(realm: Realm, step: RealmCourseStep): StepProgress {
        val exams = realm.where(RealmStepExam::class.java).equalTo("stepId", step.id).findAll()
        var completed: Boolean? = null
        var percentage: Double? = null
        var status: String? = null

        exams.forEach { exam ->
            exam.id?.let { examId ->
                realm.where(RealmSubmission::class.java)
                    .equalTo("userId", user?.id)
                    .contains("parentId", examId)
                    .equalTo("type", "exam")
                    .findAll()
            }?.forEach { submission ->
                val answers = realm.where(RealmAnswer::class.java).equalTo("submissionId", submission.id).findAll()
                var examId = submission.parentId
                if (submission.parentId?.contains("@") == true) {
                    examId = submission.parentId!!.split("@")[0]
                }
                val questions = realm.where(RealmExamQuestion::class.java).equalTo("examId", examId).findAll()
                val questionCount = questions.size
                if (questionCount > 0) {
                    completed = answers.size == questionCount
                    percentage = (answers.size.toDouble() / questionCount) * 100
                } else {
                    completed = false
                    percentage = 0.0
                }
                status = submission.status
            }
        }
        return StepProgress(step.id, completed, percentage, status)
    }
}
