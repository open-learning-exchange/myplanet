package org.ole.planet.myplanet.ui.team;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmTeamLog;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Date;
import java.util.UUID;

import io.realm.Realm;

/**
 * A simple {@link Fragment} subclass.
 */
public class TeamDetailFragment extends Fragment {

    TabLayout tabLayout;
    ViewPager viewPager;
    Realm mRealm;
    RealmMyTeam team;
    String teamId;

    public TeamDetailFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_team_detail, container, false);
        boolean isMyTeam = getArguments().getBoolean("isMyTeam", false);
        teamId = getArguments().getString("id");
        tabLayout = v.findViewById(R.id.tab_layout);
        viewPager = v.findViewById(R.id.view_pager);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        RealmMyTeam team = mRealm.where(RealmMyTeam.class).equalTo("id", getArguments().getString("id")).findFirst();
        viewPager.setAdapter(new TeamPagerAdapter(getChildFragmentManager(), team, isMyTeam));
        tabLayout.setupWithViewPager(viewPager);
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        createTeamLog();
    }

    private void createTeamLog() {
        RealmUserModel user = new UserProfileDbHandler(getActivity()).getUserModel();
        if (team == null)
            return;
        if (!mRealm.isInTransaction()) {
            mRealm.beginTransaction();
        }
        RealmTeamLog log = mRealm.createObject(RealmTeamLog.class, UUID.randomUUID().toString());
        log.setTeamId(teamId);
        log.setUser(user.getName());
        log.setCreatedOn(user.getPlanetCode());
        log.setType("teamVisit");
        log.setTeamType(team.getTeamType());
        log.setParentCode(user.getParentCode());
        log.setTime(new Date().getTime());
        mRealm.commitTransaction();
    }


}
