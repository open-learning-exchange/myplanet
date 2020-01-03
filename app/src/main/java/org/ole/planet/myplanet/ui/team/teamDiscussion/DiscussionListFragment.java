package org.ole.planet.myplanet.ui.team.teamDiscussion;


import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputLayout;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmNews;
import org.ole.planet.myplanet.model.RealmTeamNotification;
import org.ole.planet.myplanet.ui.news.AdapterNews;
import org.ole.planet.myplanet.ui.team.BaseTeamFragment;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import io.realm.Sort;

/**
 * A simple {@link Fragment} subclass.
 */
public class DiscussionListFragment extends BaseTeamFragment {

    RecyclerView rvDiscussion;
    TextView tvNodata;

    public DiscussionListFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_discussion_list, container, false);
        v.findViewById(R.id.add_message).setOnClickListener(view -> showAddMessage());
        rvDiscussion = v.findViewById(R.id.rv_discussion);
        tvNodata = v.findViewById(R.id.tv_nodata);
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        List<RealmNews> realmNewsList = mRealm.where(RealmNews.class).equalTo("viewableBy", "teams").equalTo("viewableId", team.getId()).sort("time", Sort.DESCENDING).findAll();
        int count = realmNewsList.size();
        mRealm.executeTransactionAsync(realm -> {
            RealmTeamNotification notification = realm.where(RealmTeamNotification.class).equalTo("type", "chat").equalTo("parentId", teamId).findFirst();
            if (notification == null) {
                notification = realm.createObject(RealmTeamNotification.class, UUID.randomUUID().toString());
                notification.setParentId(teamId);
                notification.setType("chat");
            }
            notification.setLastCount(count);
        });
        rvDiscussion.setLayoutManager(new LinearLayoutManager(getActivity()));
        showRecyclerView(realmNewsList);
    }

    private void showRecyclerView(List<RealmNews> realmNewsList) {
        AdapterNews adapterNews = new AdapterNews(getActivity(), realmNewsList, user, null);
        adapterNews.setmRealm(mRealm);
        adapterNews.setListener(this);
        rvDiscussion.setAdapter(adapterNews);
        showNoData(tvNodata, adapterNews.getItemCount());
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
                    map.put("messageType", team.getTeamType());
                    map.put("messagePlanetCode", team.getTeamPlanetCode());
                    RealmNews.createNews(map, mRealm, user);
                    Utilities.log("discussion created");
                    rvDiscussion.getAdapter().notifyDataSetChanged();
                }).setNegativeButton("Cancel", null).show();
    }

    @Override
    public void setData(List<RealmNews> list) {
        showRecyclerView(list);
    }
}
