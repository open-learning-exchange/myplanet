package org.ole.planet.myplanet.ui.team;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyTeam;

import java.util.List;

import io.realm.Case;
import io.realm.Realm;

/**
 * A simple {@link Fragment} subclass.
 */
public class TeamFragment extends Fragment {

    Realm mRealm;
    RecyclerView rvTeamList;
    EditText etSearch;

    public TeamFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_team, container, false);
        rvTeamList = v.findViewById(R.id.rv_team_list);
        etSearch = v.findViewById(R.id.et_search);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        return v;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mRealm != null && !mRealm.isClosed())
            mRealm.close();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        List<RealmMyTeam> list = mRealm.where(RealmMyTeam.class).isEmpty("teamId").findAll();
        rvTeamList.setLayoutManager(new LinearLayoutManager(getActivity()));
        AdapterTeamList adapterTeamList = new AdapterTeamList(getActivity(), list, mRealm);
        rvTeamList.setAdapter(adapterTeamList);
    }
}
