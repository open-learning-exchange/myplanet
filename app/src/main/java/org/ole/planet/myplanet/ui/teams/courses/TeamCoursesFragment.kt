package org.ole.planet.myplanet.ui.teams.courses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.databinding.FragmentTeamCourseBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyTeam
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
        binding.rvCourse.layoutManager = LinearLayoutManager(activity)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                teamFlow.collect { team ->
                    team?.let { setupCoursesList(it) }
                }
            }
        }
    }

    private fun setupCoursesList(currentTeam: RealmMyTeam) {
        val courses = mRealm.where(RealmMyCourse::class.java).`in`("id", currentTeam.courses.toTypedArray<String>()).findAll()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            val creator = withContext(Dispatchers.IO) {
                teamsRepository.getTeamCreator(teamId)
            }
            adapterTeamCourse = settings?.let { TeamCoursesAdapter(requireActivity(), courses.toMutableList(), creator, it) }
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
