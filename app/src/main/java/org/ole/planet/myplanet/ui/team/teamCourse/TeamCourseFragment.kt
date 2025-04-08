package org.ole.planet.myplanet.ui.team.teamCourse

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import org.ole.planet.myplanet.databinding.FragmentTeamCourseBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.ui.team.BaseTeamFragment

class TeamCourseFragment : BaseTeamFragment() {
    private lateinit var fragmentTeamCourseBinding: FragmentTeamCourseBinding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentTeamCourseBinding = FragmentTeamCourseBinding.inflate(inflater, container, false)
        return fragmentTeamCourseBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val courses = mRealm.where(RealmMyCourse::class.java).`in`("courseId", team?.courses?.toTypedArray<String>()).findAll()
        courses?.let {
            Log.d("TeamCourse", "Fetched Course List from TeamCourseFragment: $it")
        }
        val adapterTeamCourse = settings?.let { AdapterTeamCourse(requireActivity(), courses, mRealm, teamId, it) }
        fragmentTeamCourseBinding.rvCourse.layoutManager = LinearLayoutManager(activity)
        fragmentTeamCourseBinding.rvCourse.adapter = adapterTeamCourse
        if (adapterTeamCourse != null) {
            showNoData(fragmentTeamCourseBinding.tvNodata, adapterTeamCourse.itemCount, "teamCourses")
        }
    }

    fun refreshCourseList() {
        val courses = mRealm.where(RealmMyCourse::class.java)
            .`in`("courseId", team?.courses?.toTypedArray())
            .findAll()

        courses?.let {
            Log.d("TeamCourse", "Fetched Updated Course List: $it")
        }

        val adapterTeamCourse = settings?.let { AdapterTeamCourse(requireActivity(), courses, mRealm, teamId, it) }
        fragmentTeamCourseBinding.rvCourse.layoutManager = LinearLayoutManager(activity)
        fragmentTeamCourseBinding.rvCourse.adapter = adapterTeamCourse

        if (adapterTeamCourse != null) {
            showNoData(fragmentTeamCourseBinding.tvNodata, adapterTeamCourse.itemCount, "teamCourses")
        }
    }



    override fun onNewsItemClick(news: RealmNews?) {}
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }
}
