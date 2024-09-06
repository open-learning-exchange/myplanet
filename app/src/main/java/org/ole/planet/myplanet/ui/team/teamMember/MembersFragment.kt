package org.ole.planet.myplanet.ui.team.teamMember

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.base.BaseMemberFragment
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getRequestedMember
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler

class MembersFragment : BaseMemberFragment() {

    private lateinit var currentUser: RealmUserModel

    override fun onAttach(context: Context) {
        super.onAttach(context)
        currentUser = UserProfileDbHandler(context).userModel!!
    }

    override fun onNewsItemClick(news: RealmNews?) {}
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    override val list: List<RealmUserModel>
        @RequiresApi(Build.VERSION_CODES.O)
        get() = getRequestedMember(teamId, mRealm)

    override val adapter: RecyclerView.Adapter<*>
        @RequiresApi(Build.VERSION_CODES.O)
        get() = AdapterMemberRequest(requireActivity(), list.toMutableList(), mRealm, isTeamLeader()).apply { setTeamId(teamId) }

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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun isTeamLeader(): Boolean {
        val currentUserId = getCurrentUserId()
        val team = mRealm.where(RealmMyTeam::class.java)
            .equalTo("teamId", teamId)
            .equalTo("userId", currentUserId)
            .findFirst()
        return team?.isLeader == true
    }

    private fun getCurrentUserId(): String? {
        return currentUser.id
    }
}
