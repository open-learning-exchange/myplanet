package org.ole.planet.myplanet.ui.team.teamMember;


import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
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
        return new AdapterJoinedMemeber(getActivity(),getList(), mRealm, teamId);
    }

    @Override
    public RecyclerView.LayoutManager getLayoutManager() {
        return new GridLayoutManager(getActivity(), 3);
    }

}
