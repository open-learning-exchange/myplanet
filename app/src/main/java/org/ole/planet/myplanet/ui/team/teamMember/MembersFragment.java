package org.ole.planet.myplanet.ui.team.teamMember;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.ole.planet.myplanet.base.BaseMemberFragment;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmUserModel;

import java.util.List;

public class MembersFragment extends BaseMemberFragment {
    public MembersFragment() {
    }

    @Override
    public List<RealmUserModel> getList() {
        return RealmMyTeam.getRequestedMember(teamId, mRealm);
    }

    @Override
    public RecyclerView.Adapter getAdapter() {
        AdapterMemberRequest adapterMemberRequest = new AdapterMemberRequest(getActivity(), getList(), mRealm);
        adapterMemberRequest.setTeamId(teamId);
        return adapterMemberRequest;
    }

    @Override
    public RecyclerView.LayoutManager getLayoutManager() {
        return new GridLayoutManager(getActivity(), 3);
    }
}
