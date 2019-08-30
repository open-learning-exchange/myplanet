package org.ole.planet.myplanet.ui.team.teamResource;


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
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.ui.team.BaseTeamFragment;
import org.ole.planet.myplanet.ui.team.teamResource.AdapterTeamResource;

import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 */
public class TeamResourceFragment extends BaseTeamFragment {

    AdapterTeamResource adapterLibrary;
    RecyclerView rvResource;
    TextView tvNodata;
    public TeamResourceFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return  inflater.inflate(R.layout.fragment_team_resource, container, false);

    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        rvResource = getView().findViewById(R.id.rv_resource);
        tvNodata = getView().findViewById(R.id.tv_nodata);
        List<RealmMyLibrary> libraries =  mRealm.where(RealmMyLibrary.class).in("id", RealmMyTeam.getResourceIds(teamId, mRealm).toArray(new String[0])).findAll();
        adapterLibrary = new AdapterTeamResource(getActivity(),libraries, mRealm, teamId,settings);
        rvResource.setLayoutManager(new GridLayoutManager(getActivity(), 3));
        rvResource.setAdapter(adapterLibrary);
        showNoData(tvNodata, adapterLibrary.getItemCount());
    }
}
