package org.ole.planet.myplanet.ui.team.teamMember

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.base.BaseMemberFragment
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getJoinedMember
import org.ole.planet.myplanet.model.RealmUserModel

class JoinedMemberFragment : BaseMemberFragment() {
    override fun getList(): List<RealmUserModel> {
        return getJoinedMember(teamId!!, mRealm)
    }

    override fun getAdapter(): RecyclerView.Adapter<*> {
        return AdapterJoinedMember(requireActivity(), list, mRealm, teamId!!)
    }

    override fun getLayoutManager(): RecyclerView.LayoutManager {
        return GridLayoutManager(activity, 3)
    }
}
