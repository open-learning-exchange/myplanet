package org.ole.planet.myplanet.ui.team.teamMember

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.base.BaseMemberFragment
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getJoinedMember
import org.ole.planet.myplanet.model.RealmUserModel

class JoinedMemberFragment : BaseMemberFragment() {
    override val list: List<RealmUserModel>
        get() = getJoinedMember(teamId!!, mRealm)
    override val adapter: RecyclerView.Adapter<*>
        get() = AdapterJoinedMember(requireActivity(), list, mRealm, teamId!!)
    override val layoutManager: RecyclerView.LayoutManager
        get() = GridLayoutManager(activity, 3)
}
