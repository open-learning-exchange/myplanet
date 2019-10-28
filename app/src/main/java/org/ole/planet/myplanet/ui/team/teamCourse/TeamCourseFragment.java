package org.ole.planet.myplanet.ui.team.teamCourse;


import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.ui.team.BaseTeamFragment;

import io.realm.RealmResults;

/**
 * A simple {@link Fragment} subclass.
 */
public class TeamCourseFragment extends BaseTeamFragment {

    RecyclerView rvCourse;
    TextView tvNodata;

    public TeamCourseFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_team_course, container, false);
        initView(v);
        return v;
    }

    private void initView(View v) {
        rvCourse = v.findViewById(R.id.rv_course);
        tvNodata = v.findViewById(R.id.tv_nodata);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        RealmResults<RealmMyCourse> courses = mRealm.where(RealmMyCourse.class).in("id", team.getCourses().toArray(new String[0])).findAll();
        AdapterTeamCourse adapterTeamCourse = new AdapterTeamCourse(getActivity(), courses, mRealm, teamId, settings);
        rvCourse.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvCourse.setAdapter(adapterTeamCourse);
        showNoData(tvNodata, adapterTeamCourse.getItemCount());
    }
}
