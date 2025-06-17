package org.ole.planet.myplanet.ui.team.teamMember

import android.content.res.Configuration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.base.BaseMemberFragment
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getJoinedMember
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel

class JoinedMemberFragment : BaseMemberFragment() {
    override val list: List<RealmUserModel>
        get() {
            val members = getJoinedMember(teamId, mRealm).toMutableList()
            val leader = members.find { it.id == getTeamLeaderId() }
            if (leader != null) {
                members.remove(leader)
                members.add(0, leader)
            }
            return members
        }

    private fun getTeamLeaderId(): String? {
        val team = mRealm.where(RealmMyTeam::class.java)
            .equalTo("teamId", teamId)
            .equalTo("isLeader", true)
            .findFirst()
        return team?.userId
    }
    override val adapter: RecyclerView.Adapter<*>
        get() = AdapterJoinedMember(requireActivity(), list.toMutableList(), mRealm, teamId)

    override val layoutManager: RecyclerView.LayoutManager
        get() {
            val columns = when (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) {
                Configuration.SCREENLAYOUT_SIZE_LARGE -> 3
                Configuration.SCREENLAYOUT_SIZE_NORMAL -> 2
                Configuration.SCREENLAYOUT_SIZE_SMALL -> 1
                else -> 1
            }
            return GridLayoutManager(activity, columns)
        }

    override fun onNewsItemClick(news: RealmNews?) {}
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }
}
