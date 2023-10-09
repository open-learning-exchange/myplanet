package org.ole.planet.myplanet.ui.team.teamDiscussion;

import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.AlertInputBinding;
import org.ole.planet.myplanet.databinding.FragmentDiscussionListBinding;
import org.ole.planet.myplanet.model.RealmNews;
import org.ole.planet.myplanet.model.RealmTeamNotification;
import org.ole.planet.myplanet.ui.news.AdapterNews;
import org.ole.planet.myplanet.ui.team.BaseTeamFragment;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.FileUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import io.realm.Sort;

public class DiscussionListFragment extends BaseTeamFragment {
    private FragmentDiscussionListBinding fragmentDiscussionListBinding;

    public DiscussionListFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentDiscussionListBinding = FragmentDiscussionListBinding.inflate(inflater, container, false);
        fragmentDiscussionListBinding.addMessage.setOnClickListener(view -> showAddMessage());
        return fragmentDiscussionListBinding.getRoot();
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
        changeLayoutManager(getResources().getConfiguration().orientation, fragmentDiscussionListBinding.rvDiscussion);
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

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        changeLayoutManager(newConfig.orientation, fragmentDiscussionListBinding.rvDiscussion);
    }

    private void showRecyclerView(List<RealmNews> realmNewsList) {
        AdapterNews adapterNews = new AdapterNews(getActivity(), realmNewsList, user, null, true);
        adapterNews.setmRealm(mRealm);
        adapterNews.setListener(this);
        fragmentDiscussionListBinding.rvDiscussion.setAdapter(adapterNews);
        showNoData(fragmentDiscussionListBinding.tvNodata, adapterNews.getItemCount());
    }

    private void showAddMessage() {
        AlertInputBinding binding = AlertInputBinding.inflate(getLayoutInflater());
        TextInputLayout layout = binding.tlInput;

        binding.addNewsImage.setOnClickListener(v -> FileUtils.openOleFolder(this, 100));
        binding.llImage.setVisibility(Constants.showBetaFeature(Constants.KEY_NEWSADDIMAGE, getActivity()) ? View.VISIBLE : View.GONE);
        layout.setHint(getString(R.string.enter_message));

        new AlertDialog.Builder(getActivity())
                .setView(binding.getRoot())
                .setTitle(getString(R.string.add_message))
                .setPositiveButton(getString(R.string.save), (dialogInterface, i) -> {
                    String msg = layout.getEditText().getText().toString().trim();
                    if (msg.isEmpty()) {
                        Utilities.toast(getActivity(), getString(R.string.message_is_required));
                        return;
                    }
                    HashMap<String, String> map = new HashMap<>();
                    map.put("viewInId", teamId);
                    map.put("viewInSection", "teams");
                    map.put("message", msg);
                    map.put("messageType", team.getTeamType());
                    map.put("messagePlanetCode", team.getTeamPlanetCode());
                    RealmNews.createNews(map, mRealm, user, imageList);
                    Utilities.log("discussion created");
                    fragmentDiscussionListBinding.rvDiscussion.getAdapter().notifyDataSetChanged();
                    setData(getNews());
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }


    @Override
    public void setData(List<RealmNews> list) {
        showRecyclerView(list);
    }

}
