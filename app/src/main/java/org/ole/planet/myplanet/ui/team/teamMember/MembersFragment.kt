package org.ole.planet.myplanet.ui.team.teamMember

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.base.BaseMemberFragment
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getRequestedMember
import org.ole.planet.myplanet.model.RealmUserModel

class MembersFragment : BaseMemberFragment() {
    override val list: List<RealmUserModel>
        get() = getRequestedMember(teamId!!, mRealm)

    override val adapter: RecyclerView.Adapter<*>
        get() = AdapterMemberRequest(requireActivity(), list, mRealm).apply { setTeamId(teamId!!) }

    override val layoutManager: RecyclerView.LayoutManager
        get() = GridLayoutManager(activity, 3)
}
