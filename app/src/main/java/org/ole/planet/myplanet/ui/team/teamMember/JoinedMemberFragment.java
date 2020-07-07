package org.ole.planet.myplanet.ui.team.teamMember;


import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.ole.planet.myplanet.base.BaseMemberFragment;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmUserModel;

import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 */
public class JoinedMemberFragment extends BaseMemberFragment {


    public JoinedMemberFragment() {
    }


    @Override
    public List<RealmUserModel> getList() {
        return RealmMyTeam.getJoinedMemeber(teamId, mRealm);
    }

    @Override
    public RecyclerView.Adapter getAdapter() {
        return new AdapterJoinedMember(getActivity(), getList(), mRealm, teamId);
    }

    @Override
    public RecyclerView.LayoutManager getLayoutManager() {
        return new GridLayoutManager(getActivity(), 3);
    }

}
