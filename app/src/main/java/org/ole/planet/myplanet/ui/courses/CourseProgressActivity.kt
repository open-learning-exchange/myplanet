package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.databinding.ActivityCourseProgressBinding
import org.ole.planet.myplanet.model.CourseProgressData
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.service.UserSessionManager
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils

@AndroidEntryPoint
class CourseProgressActivity : BaseActivity() {
    private lateinit var binding: ActivityCourseProgressBinding
    @Inject
    lateinit var userSessionManager: UserSessionManager
    @Inject
    lateinit var coursesRepository: CoursesRepository
    var user: RealmUserModel? = null
    lateinit var courseId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCourseProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtils.setupEdgeToEdge(this, binding.root)
        initActionBar()
        courseId = intent.getStringExtra("courseId").toString()
        user = userSessionManager.userModel

        binding.rvProgress.layoutManager = GridLayoutManager(this, 4)

        lifecycleScope.launch {
            val data = coursesRepository.getCourseProgress(courseId, user?._id)
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
}
