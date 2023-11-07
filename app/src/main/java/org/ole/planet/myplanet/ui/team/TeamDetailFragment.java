package org.ole.planet.myplanet.ui.team;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.FragmentTeamDetailBinding;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmTeamLog;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Date;
import java.util.UUID;

import io.realm.Realm;

public class TeamDetailFragment extends Fragment {
    private FragmentTeamDetailBinding fragmentTeamDetailBinding;
//    ViewPager viewPager;
    Realm mRealm;
    RealmMyTeam team;
    String teamId;

    public TeamDetailFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentTeamDetailBinding = FragmentTeamDetailBinding.inflate(inflater, container, false);

        boolean isMyTeam = getArguments().getBoolean("isMyTeam", false);
        teamId = getArguments().getString("id");
        RealmUserModel user = new UserProfileDbHandler(getActivity()).getUserModel();
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        team = mRealm.where(RealmMyTeam.class).equalTo("_id", getArguments().getString("id")).findFirst();
        fragmentTeamDetailBinding.viewPager.setAdapter(new TeamPagerAdapter(getChildFragmentManager(), team, isMyTeam));
        fragmentTeamDetailBinding.tabLayout.setupWithViewPager(fragmentTeamDetailBinding.viewPager);
        if (!isMyTeam) {
            fragmentTeamDetailBinding.llActionButtons.setVisibility(View.GONE);
        } else {
            fragmentTeamDetailBinding.btnLeave.setOnClickListener(vi -> new AlertDialog.Builder(requireContext()).setMessage(R.string.confirm_exit).setPositiveButton(R.string.yes, (dialogInterface, i) -> {
                team.leave(user, mRealm);
                Utilities.toast(getActivity(), getString(R.string.left_team));
                fragmentTeamDetailBinding.viewPager.setAdapter(new TeamPagerAdapter(getChildFragmentManager(), team, false));
                fragmentTeamDetailBinding.llActionButtons.setVisibility(View.GONE);
            }).setNegativeButton(R.string.no, null).show());
        }
        if (RealmMyTeam.isTeamLeader(teamId, user.getId(), mRealm)) {
            fragmentTeamDetailBinding.btnLeave.setVisibility(View.GONE);
        }
        return fragmentTeamDetailBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        createTeamLog();
    }

    private void createTeamLog() {
        RealmUserModel user = new UserProfileDbHandler(getActivity()).getUserModel();
        if (team == null) return;
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
