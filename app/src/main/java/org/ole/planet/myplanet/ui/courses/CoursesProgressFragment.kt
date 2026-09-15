package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import org.ole.planet.myplanet.base.BaseBindingFragment
import org.ole.planet.myplanet.databinding.FragmentCoursesProgressBinding
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class CoursesProgressFragment : BaseBindingFragment<FragmentCoursesProgressBinding>(FragmentCoursesProgressBinding::inflate) {
    private val progressViewModel: ProgressViewModel by viewModels()
    private lateinit var progressAdapter: CoursesProgressAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressAdapter = CoursesProgressAdapter(requireActivity())
        binding.rvMyprogress.layoutManager = LinearLayoutManager(requireActivity())
        binding.rvMyprogress.adapter = progressAdapter
        progressViewModel.loadCourseData()
        observeCourseData()
    }

    private fun observeCourseData() {
        collectWhenStarted(progressViewModel.courseData) { courseData ->
            progressAdapter.submitList(courseData)
        }
    }
}
