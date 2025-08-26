package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import androidx.activity.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.databinding.ActivityCourseProgressBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtil

@AndroidEntryPoint
class CourseProgressActivity : BaseActivity() {
    private lateinit var binding: ActivityCourseProgressBinding
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    private val viewModel: CourseProgressViewModel by viewModels()
    var user: RealmUserModel? = null
    lateinit var courseId: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCourseProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtil.setupEdgeToEdge(this, binding.root)
        initActionBar()
        courseId = intent.getStringExtra("courseId").toString()
        user = userProfileDbHandler.userModel
        val courseProgress = viewModel.courseProgressRepository.getCourseProgress(user?.id)
        val progress = courseProgress[courseId]
        val realm = viewModel.courseProgressRepository.databaseService.realmInstance
        val course = realm.where(RealmMyCourse::class.java).equalTo("courseId", courseId).findFirst()
        if (progress != null) {
            val maxProgress = progress["max"].asInt
            if (maxProgress != 0) {
                binding.progressView.setProgress((progress["current"].asInt.toDouble() / maxProgress.toDouble() * 100).toInt(), true)
            } else {
                binding.progressView.setProgress(0, true)
            }
        }
        binding.tvCourse.text = course?.courseTitle
        binding.tvProgress.text = getString(R.string.course_progress, courseProgress[courseId]?.get("current")?.asString, courseProgress[courseId]?.get("max")?.asString)
        binding.rvProgress.layoutManager = GridLayoutManager(this, 4)
        viewModel.getCourseSteps(user?.id, courseId)
        viewModel.courseSteps.observe(this) {
            binding.rvProgress.adapter = AdapterProgressGrid(this, it)
        }
    }
}
