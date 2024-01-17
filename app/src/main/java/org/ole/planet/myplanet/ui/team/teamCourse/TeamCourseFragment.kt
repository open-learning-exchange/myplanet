package org.ole.planet.myplanet.ui.team.teamCourse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import org.ole.planet.myplanet.databinding.FragmentTeamCourseBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.ui.team.BaseTeamFragment

class TeamCourseFragment : BaseTeamFragment() {
    private lateinit var fragmentTeamCourseBinding: FragmentTeamCourseBinding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentTeamCourseBinding = FragmentTeamCourseBinding.inflate(inflater, container, false)
        return fragmentTeamCourseBinding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val courses = mRealm.where(RealmMyCourse::class.java).`in`("id", team.courses!!.toTypedArray<String>()).findAll()
        val adapterTeamCourse = AdapterTeamCourse(requireActivity(), courses, mRealm, teamId, settings)
        fragmentTeamCourseBinding.rvCourse.layoutManager = LinearLayoutManager(activity)
        fragmentTeamCourseBinding.rvCourse.adapter = adapterTeamCourse
        showNoData(fragmentTeamCourseBinding.tvNodata, adapterTeamCourse.itemCount)
    }
}
