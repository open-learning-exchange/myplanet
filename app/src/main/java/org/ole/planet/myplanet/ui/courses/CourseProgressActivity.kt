package org.ole.planet.myplanet.ui.courses

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.recyclerview.widget.GridLayoutManager
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import io.realm.RealmResults
import javax.inject.Inject
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
        databaseService.withRealm { realm ->
            val courseProgress = RealmCourseProgress.getCourseProgress(realm, user?.id)
            val progress = courseProgress[courseId]
            val course = realm.where(RealmMyCourse::class.java).equalTo("courseId", courseId).findFirst()
            if (progress != null) {
                val maxProgress = progress["max"].asInt
                val currentProgress = progress["current"].asInt
                val percentage = if (maxProgress != 0) {
                    (currentProgress.toDouble() / maxProgress.toDouble() * 100).toInt()
                } else {
                    0
                }
                val animator = ObjectAnimator.ofInt(binding.progressView, "progress", percentage)
                animator.duration = 600
                animator.interpolator = AccelerateDecelerateInterpolator()
                animator.start()
                binding.progressText.text = "$percentage%"
            }
            binding.tvCourse.text = course?.courseTitle
            binding.tvProgress.text = getString(
                R.string.course_progress,
                courseProgress[courseId]?.get("current")?.asString,
                courseProgress[courseId]?.get("max")?.asString
            )
            binding.rvProgress.layoutManager = GridLayoutManager(this, 4)
            showProgress(realm)
        }
    }

    private fun showProgress(realm: Realm) {
        val steps = realm.where(RealmCourseStep::class.java).contains("courseId", courseId).findAll()
        val array = JsonArray()
        steps.map {
            val ob = JsonObject()
            ob.addProperty("stepId", it.id)
            val exams = realm.where(RealmStepExam::class.java).equalTo("stepId", it.id).findAll()
            getExamObject(realm, exams, ob)
            array.add(ob)
        }
        binding.rvProgress.adapter = AdapterProgressGrid(this, array)
    }

    private fun getExamObject(
        realm: Realm,
        exams: RealmResults<RealmStepExam>,
        ob: JsonObject
    ) {
        exams.forEach { it ->
            it.id?.let { it1 ->
                realm.where(RealmSubmission::class.java).equalTo("userId", user?.id)
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
