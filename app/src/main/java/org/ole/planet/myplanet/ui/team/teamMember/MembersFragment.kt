package org.ole.planet.myplanet.ui.team.teamMember

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.base.BaseMemberFragment
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getRequestedMember
import org.ole.planet.myplanet.model.RealmUserModel

class MembersFragment : BaseMemberFragment() {
    override fun getList(): List<RealmUserModel> {
        return getRequestedMember(teamId!!, mRealm)
    }

    override fun getAdapter(): RecyclerView.Adapter<*> {
        val adapterMemberRequest = AdapterMemberRequest(requireActivity(), list, mRealm)
        adapterMemberRequest.setTeamId(teamId)
        return adapterMemberRequest
    }

    override fun getLayoutManager(): RecyclerView.LayoutManager {
        return GridLayoutManager(activity, 3)
    }
}
