package org.ole.planet.myplanet.ui.team.teamCourse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.databinding.FragmentTeamCourseBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.ui.team.BaseTeamFragment
import org.ole.planet.myplanet.repository.CourseRepository

@AndroidEntryPoint
class TeamCourseFragment : BaseTeamFragment() {
    private var _binding: FragmentTeamCourseBinding? = null
    private val binding get() = _binding!!
    private var adapterTeamCourse: AdapterTeamCourse? = null
    @Inject
    lateinit var courseRepository: CourseRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTeamCourseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvCourse.layoutManager = LinearLayoutManager(activity)
        setupCoursesList()
    }

    private fun setupCoursesList() {
        loadAndSubmitCourses()
    }

    fun updateCoursesList() {
        loadAndSubmitCourses()
    }

    override fun onNewsItemClick(news: RealmNews?) {}
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun loadAndSubmitCourses() {
        viewLifecycleOwner.lifecycleScope.launch {
            val courses = loadTeamCourses()
            submitCourses(courses)
        }
    }

    private suspend fun loadTeamCourses(): List<RealmMyCourse> {
        val courseIds = team?.courses?.mapNotNull { it } ?: emptyList()
        if (courseIds.isEmpty()) {
            return emptyList()
        }
        return courseRepository.getCoursesByIds(courseIds)
    }

    private fun submitCourses(courses: List<RealmMyCourse>) {
        if (adapterTeamCourse == null) {
            adapterTeamCourse = settings?.let {
                AdapterTeamCourse(requireActivity(), courses.toMutableList(), mRealm, teamId, it)
            }
            binding.rvCourse.adapter = adapterTeamCourse
        } else {
            adapterTeamCourse?.updateList(courses)
        }
        val itemCount = adapterTeamCourse?.itemCount ?: 0
        showNoData(binding.tvNodata, itemCount, "teamCourses")
    }
}
