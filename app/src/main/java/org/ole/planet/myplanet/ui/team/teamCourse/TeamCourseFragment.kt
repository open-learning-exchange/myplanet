package org.ole.planet.myplanet.ui.team.teamCourse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.databinding.FragmentTeamCourseBinding
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.ui.team.BaseTeamFragment

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
        setupCoursesList()
    }
    
    private fun setupCoursesList() {
        viewLifecycleOwner.lifecycleScope.launch {
            val courses = courseRepository.getCoursesByIds(team?.courses.orEmpty())
            adapterTeamCourse = settings?.let {
                AdapterTeamCourse(requireActivity(), courses.toMutableList(), mRealm, teamId, it)
            }
            binding.rvCourse.layoutManager = LinearLayoutManager(activity)
            binding.rvCourse.adapter = adapterTeamCourse
            adapterTeamCourse?.let {
                showNoData(binding.tvNodata, it.itemCount, "teamCourses")
            }
        }
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
}
