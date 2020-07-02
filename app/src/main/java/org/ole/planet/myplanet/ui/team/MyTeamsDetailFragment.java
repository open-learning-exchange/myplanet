package org.ole.planet.myplanet.ui.team;


import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputLayout;

import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseNewsFragment;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmNews;
import org.ole.planet.myplanet.model.RealmTeamLog;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.ui.course.TakeCourseFragment;
import org.ole.planet.myplanet.ui.library.LibraryDetailFragment;
import org.ole.planet.myplanet.ui.news.AdapterNews;
import org.ole.planet.myplanet.ui.userprofile.UserDetailFragment;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import io.realm.RealmResults;

/**
 * A simple {@link Fragment} subclass.
 */
public class MyTeamsDetailFragment extends BaseNewsFragment {

    TextView tvTitle, tvDescription;
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
    boolean isMyTeam;
    RealmResults<RealmMyLibrary> libraries;


    public MyTeamsDetailFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            teamId = getArguments().getString("id");
            isMyTeam = getArguments().getBoolean("isMyTeam", false);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_my_teams_detail, container, false);
        initializeViews(v);
        dbService = new DatabaseService(getActivity());
        mRealm = dbService.getRealmInstance();
        user = mRealm.copyFromRealm(profileDbHandler.getUserModel());
        team = mRealm.where(RealmMyTeam.class).equalTo("_id", teamId).findFirst();
        return v;
    }

    private void initializeViews(View v) {
        btnLeave = v.findViewById(R.id.btn_leave);
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
                    String msg = layout.getEditText().getText().toString().trim();
                    if (msg.isEmpty()) {
                        Utilities.toast(getActivity(), "Message is required");
                        return;
                    }
                    HashMap<String, String> map = new HashMap<>();
                    map.put("viewableBy", "teams");
                    map.put("viewableId", teamId);
                    map.put("message", msg);
                    map.put("messageType", team.getTeamType());
                    map.put("messagePlanetCode", team.getTeamPlanetCode());
                    RealmNews.createNews(map, mRealm, user, imageList);
                    rvDiscussion.getAdapter().notifyDataSetChanged();
                }).setNegativeButton("Cancel", null).show();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        tvTitle.setText(team.getName());
        tvDescription.setText(team.getDescription());
        setTeamList();
    }

    private void setTeamList() {
        List<RealmUserModel> users = RealmMyTeam.getUsers(teamId, mRealm, "");
        createTeamLog();
        List<RealmUserModel> reqUsers = getRequestedTeamList(team.getRequests());
        List<RealmNews> realmNewsList = mRealm.where(RealmNews.class).isEmpty("replyTo").equalTo("viewableBy", "teams").equalTo("viewableId", team.get_id()).findAll();
        rvDiscussion.setLayoutManager(new LinearLayoutManager(getActivity()));
        showRecyclerView(realmNewsList);
        listContent.setVisibility(View.GONE);
        RealmResults<RealmMyCourse> courses = mRealm.where(RealmMyCourse.class).in("id", team.getCourses().toArray(new String[0])).findAll();
        libraries = mRealm.where(RealmMyLibrary.class).in("id", RealmMyTeam.getResourceIds(teamId, mRealm).toArray(new String[0])).findAll();

        tabLayout.getTabAt(1).setText(String.format("Joined Members : (%s)", users.size()));
        tabLayout.getTabAt(3).setText(String.format("Courses : (%s)", courses.size()));
        tabLayout.getTabAt(2).setText(String.format("Requested Members : (%s)", reqUsers.size()));
        tabLayout.getTabAt(4).setText(String.format("Resources : (%s)", libraries.size()));

        if (!isMyTeam) {
            try {
                ((ViewGroup) tabLayout.getChildAt(0)).getChildAt(0).setVisibility(View.GONE);
                ((ViewGroup) tabLayout.getChildAt(4)).getChildAt(0).setVisibility(View.GONE);
                tabLayout.getTabAt(1).select();
            } catch (Exception e) {
            }
        }
        setTabListener(users, courses, reqUsers);

    }

    private void createTeamLog() {
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

    private void showRecyclerView(List<RealmNews> realmNewsList) {
        AdapterNews adapterNews = new AdapterNews(getActivity(), realmNewsList, user, null);
        adapterNews.setmRealm(mRealm);
        adapterNews.setListener(this);
        rvDiscussion.setAdapter(adapterNews);
        llRv.setVisibility(View.VISIBLE);
    }

    private void setTabListener(List<RealmUserModel> users, RealmResults<RealmMyCourse> courses, List<RealmUserModel> reqUsers) {
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
                else if (tab.getPosition() == 3) setCourseList(tab, courses);
                else if (tab.getPosition() == 4) setLibraryList(tab);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void setLibraryList(TabLayout.Tab tab) {
        hideRv(tab, String.format("Resources : (%s)", libraries.size()));
        listContent.setAdapter(new ArrayAdapter<RealmMyLibrary>(getActivity(), android.R.layout.simple_list_item_1, libraries));
        listContent.setOnItemClickListener((adapterView, view, i, l) -> {
            if (homeItemClickListener != null) {
                LibraryDetailFragment f = new LibraryDetailFragment();
                Bundle b = new Bundle();
                b.putString("libraryId", libraries.get(i).getId());
                b.putString("openFrom", team.getTeamType() + "-" + team.getTitle() );
                f.setArguments(b);
                homeItemClickListener.openCallFragment(f);
            }

        });
    }

    private void setCourseList(TabLayout.Tab tab, RealmResults<RealmMyCourse> courses) {
        hideRv(tab, String.format("Courses : (%s)", courses.size()));
        listContent.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, courses));
        listContent.setOnItemClickListener((adapterView, view, i, l) -> {
            if (homeItemClickListener != null) {
                openFragment(courses.get(i).getCourseId(), new TakeCourseFragment());
            }
        });
    }

    private void hideRv(TabLayout.Tab tab, String s) {
        listContent.setVisibility(View.VISIBLE);
        llRv.setVisibility(View.GONE);
        tab.setText(s);
    }

    private void setListContent(TabLayout.Tab tab, String s, List<RealmUserModel> data) {
        listContent.setVisibility(View.VISIBLE);
        llRv.setVisibility(View.GONE);
        tab.setText(s);
        listContent.setAdapter(new ArrayAdapter<RealmUserModel>(getActivity(), android.R.layout.simple_list_item_1, data) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                if (convertView == null)
                    convertView = LayoutInflater.from(getActivity()).inflate(android.R.layout.simple_list_item_1, parent, false);
                TextView tv = convertView.findViewById(android.R.id.text1);
                tv.setText(getItem(position).getName() + " (" + RealmTeamLog.getVisitCount(mRealm, getItem(position).getName(), teamId) + " visits )");
                return convertView;
            }
        });
        listContent.setOnItemClickListener((adapterView, view, i, l) -> {
            openFragment(data.get(i).getId(), new UserDetailFragment());
        });
    }

    private void openFragment(String id, Fragment f) {
        Bundle b = new Bundle();
        b.putString("id", id);
        f.setArguments(b);
        homeItemClickListener.openCallFragment(f);
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
        }
        return new ArrayList<>();
    }

    @Override
    public void setData(List<RealmNews> list) {
        showRecyclerView(list);
    }

}
