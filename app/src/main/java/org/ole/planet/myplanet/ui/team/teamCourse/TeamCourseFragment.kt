package org.ole.planet.myplanet.ui.team.teamCourse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.databinding.FragmentTeamCourseBinding
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.ui.team.BaseTeamFragment

@AndroidEntryPoint
class TeamCourseFragment : BaseTeamFragment() {
    private var _binding: FragmentTeamCourseBinding? = null
    private val binding get() = _binding!!
    private var adapterTeamCourse: AdapterTeamCourse? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTeamCourseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCoursesList()
    }

    private fun setupCoursesList() {
        binding.rvCourse.layoutManager = LinearLayoutManager(activity)
        team?.let { currentTeam ->
            val courseIds = currentTeam.courses?.toList() ?: emptyList()
            if (courseIds.isEmpty()) {
                showNoData(binding.tvNodata, 0, "teamCourses")
                return@let
            }

            viewLifecycleOwner.lifecycleScope.launch {
                val courses = courseRepository.getCoursesByTeamCourseIds(courseIds)
                val teamCreator = currentTeam.createdBy ?: ""
                adapterTeamCourse = settings?.let {
                    AdapterTeamCourse(requireActivity(), courses, teamCreator, it)
                }
                binding.rvCourse.adapter = adapterTeamCourse
                adapterTeamCourse?.let {
                    showNoData(binding.tvNodata, it.itemCount, "teamCourses")
                }
            }
        } ?: showNoData(binding.tvNodata, 0, "teamCourses")
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
