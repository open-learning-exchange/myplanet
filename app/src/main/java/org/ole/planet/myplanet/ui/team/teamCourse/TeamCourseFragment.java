package org.ole.planet.myplanet.ui.team.teamCourse;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.ole.planet.myplanet.databinding.FragmentTeamCourseBinding;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.ui.team.BaseTeamFragment;

import io.realm.RealmResults;

public class TeamCourseFragment extends BaseTeamFragment {
    private FragmentTeamCourseBinding fragmentTeamCourseBinding;

    public TeamCourseFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentTeamCourseBinding = FragmentTeamCourseBinding.inflate(inflater, container, false);
        return fragmentTeamCourseBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        RealmResults<RealmMyCourse> courses = mRealm.where(RealmMyCourse.class).in("id", team.courses.toArray(new String[0])).findAll();
        AdapterTeamCourse adapterTeamCourse = new AdapterTeamCourse(getActivity(), courses, mRealm, teamId, settings);
        fragmentTeamCourseBinding.rvCourse.setLayoutManager(new LinearLayoutManager(getActivity()));
        fragmentTeamCourseBinding.rvCourse.setAdapter(adapterTeamCourse);
        showNoData(fragmentTeamCourseBinding.tvNodata, adapterTeamCourse.getItemCount());
    }
}
