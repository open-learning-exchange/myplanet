package org.ole.planet.myplanet.ui.team.teamCourse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import io.realm.RealmChangeListener
import io.realm.RealmResults
import org.ole.planet.myplanet.databinding.FragmentTeamCourseBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.ui.team.BaseTeamFragment

class TeamCourseFragment : BaseTeamFragment() {
    private var _binding: FragmentTeamCourseBinding? = null
    private val binding get() = _binding!!
    private var adapterTeamCourse: AdapterTeamCourse? = null
    private var courses: RealmResults<RealmMyCourse>? = null
    private val courseChangeListener = RealmChangeListener<RealmResults<RealmMyCourse>> { results ->
        adapterTeamCourse?.updateList(results)
        showNoData(binding.tvNodata, results.size, "teamCourses")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTeamCourseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCoursesList()
    }

    private fun setupCoursesList() {
        courses = mRealm.where(RealmMyCourse::class.java)
            .`in`("id", team?.courses?.toTypedArray<String>())
            .findAllAsync()
        courses?.addChangeListener(courseChangeListener)
        adapterTeamCourse = settings?.let {
            AdapterTeamCourse(requireActivity(), courses?.toList() ?: emptyList(), mRealm, teamId, it)
        }
        binding.rvCourse.layoutManager = LinearLayoutManager(activity)
        binding.rvCourse.adapter = adapterTeamCourse
        adapterTeamCourse?.let {
            showNoData(binding.tvNodata, it.itemCount, "teamCourses")
        }
    }

    override fun onNewsItemClick(news: RealmNews?) {}
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    override fun onDestroyView() {
        courses?.removeChangeListener(courseChangeListener)
        _binding = null
        super.onDestroyView()
    }
}
