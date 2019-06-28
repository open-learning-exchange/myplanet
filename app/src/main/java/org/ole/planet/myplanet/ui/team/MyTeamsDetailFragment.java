package org.ole.planet.myplanet.ui.team;


import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseNewsFragment;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMeetup;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmNews;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.course.CourseDetailFragment;
import org.ole.planet.myplanet.ui.course.TakeCourseFragment;
import org.ole.planet.myplanet.ui.news.AdapterNews;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.Sort;
import okhttp3.internal.Util;

/**
 * A simple {@link Fragment} subclass.
 */
public class MyTeamsDetailFragment extends BaseNewsFragment implements View.OnClickListener {

    TextView tvTitle, tvDescription;

    UserProfileDbHandler profileDbHandler;
    RealmUserModel user;
    String teamId;
    RealmMyTeam team;
    Button btnLeave;
    Button btnInvite;
    ListView listContent;
    TabLayout tabLayout;
    DatabaseService dbService;
    RecyclerView rvDiscussion;
    LinearLayout llRv;


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
//        btnShowMain = v.findViewById(R.id.btn_main_conversation);
        btnLeave.setOnClickListener(this);
        llRv = v.findViewById(R.id.ll_rv);
        btnLeave.setVisibility(Constants.showBetaFeature(Constants.KEY_MEETUPS, getActivity()) ? View.VISIBLE : View.GONE);
        btnInvite = v.findViewById(R.id.btn_invite);
        btnInvite.setVisibility(Constants.showBetaFeature(Constants.KEY_MEETUPS, getActivity()) ? View.VISIBLE : View.GONE);
        rvDiscussion = v.findViewById(R.id.rv_discussion);
        tvDescription = v.findViewById(R.id.description);
        tabLayout = v.findViewById(R.id.tab_layout);
        listContent = v.findViewById(R.id.list_content);
        tvTitle = v.findViewById(R.id.title);
        v.findViewById(R.id.add_message).setOnClickListener(view -> {
            showAddMessage();
        });

    }

    private void showAddMessage() {
        View v = getLayoutInflater().inflate(R.layout.alert_input, null);
        TextInputLayout layout = v.findViewById(R.id.tl_input);
        layout.setHint(getString(R.string.enter_message));
        new AlertDialog.Builder(getActivity())
                .setView(v)
                .setTitle("Add Message")
                .setPositiveButton("Save", (dialogInterface, i) -> {
                    String msg = layout.getEditText().getText().toString();
                    if (msg.isEmpty()) {
                        Utilities.toast(getActivity(), "Message is required");
                        return;
                    }
                    HashMap<String, String> map = new HashMap<>();
                    map.put("viewableBy", "teams");
                    map.put("viewableId", teamId);
                    map.put("message", msg);
                    RealmNews.createNews(map, mRealm, user);
                    rvDiscussion.getAdapter().notifyDataSetChanged();
                }).setNegativeButton("Cancel", null).show();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setUpTeamsData();
        setTeamList();
    }

    private void setTeamList() {
        RealmResults<RealmUserModel> users = mRealm.where(RealmUserModel.class).in("id", team.getUserId().toArray(new String[0])).findAll();
        List<RealmUserModel> reqUsers = getRequestedTeamList(team.getRequests());
        List<RealmNews> realmNewsList = mRealm.where(RealmNews.class).equalTo("viewableBy", "teams").equalTo("viewableId", team.getTeamId()).findAll();
        Utilities.log("news list size " + realmNewsList.size());
        rvDiscussion.setLayoutManager(new LinearLayoutManager(getActivity()));
        showRecyclerView(realmNewsList);
//        btnShowMain.setOnClickListener(view -> {
//            showRecyclerView(realmNewsList);
//            btnShowMain.setVisibility(View.GONE);
//        });
        listContent.setVisibility(View.GONE);
        RealmResults<RealmMyCourse> courses = mRealm.where(RealmMyCourse.class).in("id", team.getCourses().toArray(new String[0])).findAll();
        tabLayout.getTabAt(1).setText(String.format("Joined Members : (%s)", users.size()));
        tabLayout.getTabAt(3).setText(String.format("Courses : (%s)", courses.size()));
        tabLayout.getTabAt(2).setText(String.format("Requested Members : (%s)", reqUsers.size()));
        setTabListener(users, courses, reqUsers);
    }

    private void showRecyclerView(List<RealmNews> realmNewsList) {
        AdapterNews adapterNews = new AdapterNews(getActivity(), realmNewsList, user, null);
        adapterNews.setmRealm(mRealm);
        adapterNews.setListener(this);
        rvDiscussion.setAdapter(adapterNews);
        llRv.setVisibility(View.VISIBLE);
    }

    private void setTabListener(RealmResults<RealmUserModel> users, RealmResults<RealmMyCourse> courses, List<RealmUserModel> reqUsers) {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    listContent.setVisibility(View.GONE);
                    llRv.setVisibility(View.VISIBLE);
                } else if (tab.getPosition() == 1)
                    setListContent(tab, String.format("Joined Members : (%s)", users.size()), users);
                else if (tab.getPosition() == 2)
                    setListContent(tab, String.format("Requested Members : (%s)", reqUsers.size()), reqUsers);
                else setCourseList(tab, courses);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void setCourseList(TabLayout.Tab tab, RealmResults<RealmMyCourse> courses) {
        tab.setText(String.format("Courses : (%s)", courses.size()));
        listContent.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, courses));
        listContent.setOnItemClickListener((adapterView, view, i, l) -> {
            if (homeItemClickListener != null) {
                Bundle b = new Bundle();
                TakeCourseFragment f = new TakeCourseFragment();
                b.putString("id", courses.get(i).getCourseId());
                f.setArguments(b);
                homeItemClickListener.openCallFragment(f);
            }
        });
    }

    private void setListContent(TabLayout.Tab tab, String s, List<RealmUserModel> data) {
        listContent.setVisibility(View.VISIBLE);
        llRv.setVisibility(View.GONE);
        tab.setText(s);
        listContent.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, data));
        listContent.setOnItemClickListener(null);
    }


    private void setUpTeamsData() {
        tvTitle.setText(team.getName());
        tvDescription.setText(team.getDescription());
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
        } catch (JSONException e) {}
    }

    public List<RealmUserModel> getRequestedTeamList(String req) {
        try {
            JSONArray array = new JSONArray(req);
            String[] ids = new String[array.length()];
            for (int i = 0; i < array.length(); i++) {
                ids[i] = array.get(i).toString();
            }
            return mRealm.where(RealmUserModel.class).in("id", ids).findAll();
        } catch (Exception e) {}
        return new ArrayList<>();
    }

    @Override
    public void setData(List<RealmNews> list) {
        showRecyclerView(list);
    }

//    @Override
//    public void showReply(RealmNews news) {
//        List<RealmNews> list = mRealm.where(RealmNews.class).sort("time", Sort.DESCENDING)
//                .equalTo("replyTo", news.getId(), Case.INSENSITIVE)
//                .findAll();
//        showRecyclerView(list);
//        btnShowMain.setVisibility(View.VISIBLE);
//    }
}
