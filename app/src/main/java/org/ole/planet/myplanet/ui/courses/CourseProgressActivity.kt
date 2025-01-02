package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import androidx.recyclerview.widget.GridLayoutManager
import com.google.gson.*
import io.realm.kotlin.Realm
import io.realm.kotlin.query.RealmResults
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.ole.planet.myplanet.*
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.databinding.ActivityCourseProgressBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.*
import org.ole.planet.myplanet.service.UserProfileDbHandler

class CourseProgressActivity : BaseActivity() {
    private lateinit var activityCourseProgressBinding: ActivityCourseProgressBinding
    lateinit var realm: Realm
    var user: RealmUserModel? = null
    lateinit var courseId: String
    private val scope = MainApplication.applicationScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityCourseProgressBinding = ActivityCourseProgressBinding.inflate(layoutInflater)
        setContentView(activityCourseProgressBinding.root)
        initActionBar()
        courseId = intent.getStringExtra("courseId").toString()
        realm = DatabaseService().realmInstance
        user = UserProfileDbHandler(this).userModel
        scope.launch {
            setupCourseProgress()
            showProgress()
        }
    }

    private suspend fun setupCourseProgress() {
        val course = realm.query<RealmMyCourse>(RealmMyCourse::class, "courseId == $0", courseId).first().find()

        RealmCourseProgress.getCourseProgress(realm, user?.id ?: "")
            .collectLatest { progressMap ->
                progressMap[courseId]?.let { progress ->
                    val maxProgress = progress.get("max").asInt
                    val currentProgress = if (maxProgress != 0) {
                        (progress.get("current").asInt.toDouble() / maxProgress.toDouble() * 100).toInt()
                    } else 0
                    activityCourseProgressBinding.progressView.setProgress(currentProgress, true)

                    activityCourseProgressBinding.apply {
                        tvCourse.text = course?.courseTitle
                        tvProgress.text = getString(R.string.course_progress, progress.get("current").asString, progress.get("max").asString)
                        rvProgress.layoutManager = GridLayoutManager(this@CourseProgressActivity, 4)
                    }
                }
            }
    }

    private fun showProgress() {
        val steps = realm.query<RealmCourseStep>(RealmCourseStep::class, "courseId CONTAINS $0", courseId).find()
        val array = JsonArray()

        steps.forEach { step ->
            val ob = JsonObject()
            ob.addProperty("stepId", step.id)

            val exams = realm.query<RealmStepExam>(RealmStepExam::class, "stepId == $0", step.id).find()
            getExamObject(exams, ob)
            array.add(ob)
        }

        activityCourseProgressBinding.rvProgress.adapter = AdapterProgressGrid(this, array)
    }

    private fun getExamObject(exams: RealmResults<RealmStepExam>, ob: JsonObject) {
        exams.forEach { exam ->
            exam.id?.let { examId ->
                val submissions = realm.query<RealmSubmission>(RealmSubmission::class,
                    "userId == $0 AND parentId CONTAINS $1 AND type == $2", user?.id ?: "",
                    examId, "exam").find()

                submissions.forEach { submission ->
                    val answers = realm.query<RealmAnswer>(RealmAnswer::class, "submissionId == $0", submission.id).find()
                    val parentId = submission.parentId?.split("@")?.firstOrNull() ?: submission.parentId
                    val questions = realm.query<RealmExamQuestion>(RealmExamQuestion::class, "examId == $0", parentId).find()

                    if (questions.isNotEmpty()) {
                        ob.apply {
                            addProperty("completed", questions.size == answers.size)
                            addProperty("percentage", (answers.size.toFloat() / questions.size) * 100)
                            addProperty("status", submission.status)
                        }
                    }
                }
            }
        }
    }
}