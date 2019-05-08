package org.ole.planet.myplanet.ui.team;


import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMeetup;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.course.CourseDetailFragment;
import org.ole.planet.myplanet.ui.course.TakeCourseFragment;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmResults;
import okhttp3.internal.Util;

/**
 * A simple {@link Fragment} subclass.
 */
public class MyTeamsDetailFragment extends Fragment implements View.OnClickListener {

    TextView tvTitle, tvDescription, tvJoined, tvRequested, tvCourses;
    UserProfileDbHandler profileDbHandler;
    RealmUserModel user;
    String teamId;
    Realm mRealm;
    RealmMyTeam team;
    Button btnLeave;
    Button btnInvite;
    ListView lvJoined, lvRequested, lvCourses;
    DatabaseService dbService;
    OnHomeItemClickListener homeItemClickListener;
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnHomeItemClickListener)
            homeItemClickListener = (OnHomeItemClickListener) context;
    }

    public MyTeamsDetailFragment() {
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            teamId = getArguments().getString("id");
        }
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_my_teams_detail, container, false);
        initializeViews(v);
        dbService = new DatabaseService(getActivity());
        mRealm = dbService.getRealmInstance();
        profileDbHandler = new UserProfileDbHandler(getActivity());
        user = mRealm.copyFromRealm(profileDbHandler.getUserModel());
        team = mRealm.where(RealmMyTeam.class).equalTo("teamId", teamId).findFirst();
        return v;
    }

    private void initializeViews(View v) {
        btnLeave = v.findViewById(R.id.btn_leave);
        btnLeave.setOnClickListener(this);
        btnLeave.setVisibility(Constants.showBetaFeature(Constants.KEY_MEETUPS, getActivity()) ? View.VISIBLE :View.GONE );
        btnInvite = v.findViewById(R.id.btn_invite);
        btnInvite.setVisibility(Constants.showBetaFeature(Constants.KEY_MEETUPS, getActivity()) ? View.VISIBLE :View.GONE );
        tvDescription = v.findViewById(R.id.description);
        tvJoined = v.findViewById(R.id.tv_joined);
        tvRequested = v.findViewById(R.id.tv_requested);
        tvCourses= v.findViewById(R.id.tv_courses);
        lvJoined = v.findViewById(R.id.list_joined);
        lvCourses= v.findViewById(R.id.list_courses);
        lvRequested = v.findViewById(R.id.list_requested);
        tvTitle = v.findViewById(R.id.title);

    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setUpTeamsData();
        setTeamList();
    }

    private void setTeamList() {
        String[] ids = team.getUserId().toArray(new String[0]);
        RealmResults<RealmUserModel> users = mRealm.where(RealmUserModel.class).in("id", ids).findAll();

        String[] courseIds = team.getCourses().toArray(new String[0]);
        RealmResults<RealmMyCourse> courses = mRealm.where(RealmMyCourse.class).in("id", courseIds).findAll();


        lvJoined.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, users));
        tvJoined.setText(String.format("Joined Members : (%s)", users.size() == 0 ? "(0)\nNo members has joined this meet up" : users.size()));

        tvTitle.setText(team.getName());
        tvDescription.setText(team.getDescription());

        tvCourses.setText(String.format("Courses : (%s)", courses.size() == 0 ? "(0)\nNo courses " : courses.size()));
        lvCourses.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, courses));
        lvCourses.setOnItemClickListener((adapterView, view, i, l) -> {
            if (homeItemClickListener!=null){
                Bundle b = new Bundle();
                TakeCourseFragment f = new TakeCourseFragment();
                b.putString("id", courses.get(i).getCourseId());
                f.setArguments(b);
                homeItemClickListener.openCallFragment(f);
            }
        });
        List<RealmUserModel> reqUsers = getRequestedTeamList(team.getRequests());
        tvRequested.setText(String.format("Requested Members : %s", reqUsers.size() == 0 ? "(0)\nThere are no requests to join this team.\n" +
                "\n" : reqUsers.size()));
        lvRequested.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, reqUsers));

    }


    private void setUpTeamsData() {

    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btn_leave) {
            leaveJoinTeam();

        }
    }

    private void leaveJoinTeam() {
        mRealm.executeTransaction(realm -> {
            if (team.getUserId().isEmpty()) {
                requestToJoin();
                btnLeave.setText("Request Pending");
            } else {
                team.setUserId("");
                btnLeave.setText("Request to Join");
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        profileDbHandler.onDestory();
    }

    private void requestToJoin() {
        try {
            JSONArray array = new JSONArray(team.getRequests());
            if (!team.getRequests().contains(user.getId())) {
                array.put(user.getId());
            }
            team.setRequests(array.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public List<RealmUserModel> getRequestedTeamList(String req) {
        try {
            JSONArray array = new JSONArray(req);
            String[] ids = new String[array.length()];
            for (int i = 0; i < array.length(); i++) {
                ids[i] = array.get(i).toString();
            }
            return mRealm.where(RealmUserModel.class).in("id", ids).findAll();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

}
