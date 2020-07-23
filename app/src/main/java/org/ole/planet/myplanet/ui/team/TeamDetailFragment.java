package org.ole.planet.myplanet.ui.team;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import org.ole.planet.myplanet.MainApplication;
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
    LinearLayout llButtons;

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
        llButtons = v.findViewById(R.id.ll_action_buttons);
        Button leave = v.findViewById(R.id.btn_leave);
        RealmUserModel user = new UserProfileDbHandler(getActivity()).getUserModel();
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        team = mRealm.where(RealmMyTeam.class).equalTo("_id", getArguments().getString("id")).findFirst();
        viewPager.setAdapter(new TeamPagerAdapter(getChildFragmentManager(), team, isMyTeam));
        tabLayout.setupWithViewPager(viewPager);
        if (!isMyTeam) {
            llButtons.setVisibility(View.GONE);
        } else {
            leave.setOnClickListener(vi -> new AlertDialog.Builder(requireContext()).setMessage(R.string.confirm_exit).setPositiveButton("Yes", (dialogInterface, i) -> {
                team.leave(user, mRealm);
                Utilities.toast(getActivity(), "Left team");
                viewPager.setAdapter(new TeamPagerAdapter(getChildFragmentManager(), team, false));
                llButtons.setVisibility(View.GONE);
            }).setNegativeButton("No", null).show());

            v.findViewById(R.id.btn_add_doc).setOnClickListener(view -> {
                MainApplication.showDownload = true;
                viewPager.setCurrentItem(6);
                MainApplication.showDownload = false;
                if (MainApplication.listener != null) {
                    MainApplication.listener.onAddDocument();
                }

            });
        }
        if (RealmMyTeam.isTeamLeader(teamId, user.getId(), mRealm)) {
            leave.setVisibility(View.GONE);
        }
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
        Utilities.log("Crete team log");
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
