package org.ole.planet.myplanet.ui.team.teamCourse

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import org.ole.planet.myplanet.databinding.FragmentTeamCourseBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.ui.team.BaseTeamFragment

@RequiresApi(Build.VERSION_CODES.O)
class TeamCourseFragment : BaseTeamFragment() {
    private lateinit var fragmentTeamCourseBinding: FragmentTeamCourseBinding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentTeamCourseBinding = FragmentTeamCourseBinding.inflate(inflater, container, false)
        return fragmentTeamCourseBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val courses = mRealm.where(RealmMyCourse::class.java).`in`("id", team.courses?.toTypedArray<String>()).findAll()
        val adapterTeamCourse = settings?.let { AdapterTeamCourse(requireActivity(), courses, mRealm, teamId, it) }
        fragmentTeamCourseBinding.rvCourse.layoutManager = LinearLayoutManager(activity)
        fragmentTeamCourseBinding.rvCourse.adapter = adapterTeamCourse
        if (adapterTeamCourse != null) {
            showNoData(fragmentTeamCourseBinding.tvNodata, adapterTeamCourse.itemCount, "teamCourses")
        }
    }

    override fun onNewsItemClick(news: RealmNews?) {}
}
