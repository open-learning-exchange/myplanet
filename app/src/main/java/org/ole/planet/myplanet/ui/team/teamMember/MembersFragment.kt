package org.ole.planet.myplanet.ui.team.teamMember

import android.content.res.Configuration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.base.BaseMemberFragment
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getRequestedMember
import org.ole.planet.myplanet.model.RealmUserModel

class MembersFragment : BaseMemberFragment() {
    override val list: List<RealmUserModel>
        get() = getRequestedMember(teamId, mRealm)

    override val adapter: RecyclerView.Adapter<*>
        get() = AdapterMemberRequest(requireActivity(), list.toMutableList(), mRealm).apply { setTeamId(teamId) }

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
}
