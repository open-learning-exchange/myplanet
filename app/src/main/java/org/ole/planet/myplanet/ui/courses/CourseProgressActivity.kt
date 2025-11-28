package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.gson.JsonArray
import dagger.hilt.android.AndroidEntryPoint
import android.view.View
import android.widget.Toast
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.databinding.ActivityCourseProgressBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils

@AndroidEntryPoint
class CourseProgressActivity : BaseActivity() {
    private lateinit var binding: ActivityCourseProgressBinding
    private val viewModel: CourseProgressViewModel by viewModels()
    private lateinit var courseId: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCourseProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtils.setupEdgeToEdge(this, binding.root)
        initActionBar()
        courseId = intent.getStringExtra("courseId").toString()
        viewModel.getCourseProgress(courseId)
        binding.rvProgress.layoutManager = GridLayoutManager(this, 4)
        observeCourseProgress()
    }

    private fun observeCourseProgress() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is CourseProgressState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    is CourseProgressState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.tvCourse.text = state.courseTitle
                        binding.progressView.setProgress(state.progress, true)
                        binding.tvProgress.text = getString(
                            R.string.course_progress,
                            state.currentProgress.toString(),
                            state.maxProgress.toString()
                        )
                        binding.rvProgress.adapter = AdapterProgressGrid(this@CourseProgressActivity, state.stepProgress)
                    }
                    is CourseProgressState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@CourseProgressActivity, state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
