package org.ole.planet.myplanet.ui.team.teamMember;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.ui.team.BaseTeamFragment;

import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 */
public class JoinedMemberFragment extends BaseTeamFragment {

    RecyclerView rvMember;
    TextView tvNodata;
    public JoinedMemberFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v =  inflater.inflate(R.layout.fragment_members, container, false);
        rvMember = v.findViewById(R.id.rv_member);
        tvNodata = v.findViewById(R.id.tv_nodata);
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        List<RealmUserModel> joinedMember = RealmMyTeam.getJoinedMemeber(teamId, mRealm);
        rvMember.setLayoutManager(new GridLayoutManager(getActivity(), 3));
        rvMember.setAdapter(new AdapterJoinedMemeber(getActivity(),joinedMember, mRealm));
        showNoData(tvNodata, joinedMember.size());
    }
}
