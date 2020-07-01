package org.ole.planet.myplanet.ui.team.teamDiscussion;


import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmNews;
import org.ole.planet.myplanet.model.RealmTeamNotification;
import org.ole.planet.myplanet.ui.news.AdapterNews;
import org.ole.planet.myplanet.ui.team.BaseTeamFragment;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.FileUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import io.realm.Sort;

import static android.app.Activity.RESULT_OK;

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
        List<RealmNews> realmNewsList = getNews();
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
        changeLayoutManager(getResources().getConfiguration().orientation, rvDiscussion);
        showRecyclerView(realmNewsList);
    }

    private List<RealmNews> getNews() {
        List<RealmNews> realmNewsList = mRealm.where(RealmNews.class).isEmpty("replyTo").sort("time", Sort.DESCENDING).findAll();
        List<RealmNews> list = new ArrayList<>();
        for (RealmNews news : realmNewsList) {
            if (!TextUtils.isEmpty(news.getViewableBy()) && news.getViewableBy().equalsIgnoreCase("teams") && news.getViewableId().equalsIgnoreCase(team.get_id())) {
                list.add(news);
            } else if (!TextUtils.isEmpty(news.getViewIn())) {
                JsonArray ar = new Gson().fromJson(news.getViewIn(), JsonArray.class);
                for (JsonElement e : ar) {
                    JsonObject ob = e.getAsJsonObject();
                    if (ob.get("_id").getAsString().equalsIgnoreCase(team.get_id())) {
                        list.add(news);
                    }
                }
            }
        }
        return list;
    }


//    public void changeLayoutManager(int orientation) {
//        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
//            rvDiscussion.setLayoutManager(new GridLayoutManager(getActivity(), 2));
//        } else {
//            rvDiscussion.setLayoutManager(new LinearLayoutManager(getActivity()));
//        }
//    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        changeLayoutManager(newConfig.orientation, rvDiscussion);
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
//        thumb = v.findViewById(R.id.thumb);
        v.findViewById(R.id.add_news_image).setOnClickListener(vi -> FileUtils.openOleFolder(this, 100));
        v.findViewById(R.id.ll_image).setVisibility(Constants.showBetaFeature(Constants.KEY_NEWSADDIMAGE, getActivity()) ? View.VISIBLE : View.GONE);
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
                    map.put("viewInId", teamId);
                    map.put("viewInSection", "teams");
                    map.put("message", msg);
                    map.put("messageType", team.getTeamType());
                    map.put("messagePlanetCode", team.getTeamPlanetCode());
//                    map.put("imageUrl", imageUrl);
//                    map.put("imageName", imageName);
                    RealmNews.createNews(map, mRealm, user, imageList);
                    Utilities.log("discussion created");
                    rvDiscussion.getAdapter().notifyDataSetChanged();
                }).setNegativeButton("Cancel", null).show();
    }

    @Override
    public void setData(List<RealmNews> list) {
        showRecyclerView(list);
    }


}
