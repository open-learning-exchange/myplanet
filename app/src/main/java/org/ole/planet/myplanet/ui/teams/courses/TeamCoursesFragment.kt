package org.ole.planet.myplanet.ui.teams.courses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import org.ole.planet.myplanet.databinding.FragmentTeamCourseBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.ui.teams.BaseTeamFragment

class TeamCoursesFragment : BaseTeamFragment() {
    private var _binding: FragmentTeamCourseBinding? = null
    private val binding get() = _binding!!
    private var adapterTeamCourse: TeamCoursesAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTeamCourseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCoursesList()
    }
    
    private fun setupCoursesList() {
        val courses = mRealm.where(RealmMyCourse::class.java).`in`("id", team?.courses?.toTypedArray<String>()).findAll()
        val courseList = mRealm.copyFromRealm(courses)
        adapterTeamCourse = settings?.let { TeamCoursesAdapter(requireActivity(), mRealm, teamId, it) }
        binding.rvCourse.layoutManager = LinearLayoutManager(activity)
        binding.rvCourse.adapter = adapterTeamCourse
        adapterTeamCourse?.submitList(courseList)
        showNoData(binding.tvNodata, courseList.size, "teamCourses")
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
