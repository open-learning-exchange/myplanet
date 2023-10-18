package org.ole.planet.myplanet.ui.news;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseNewsFragment;
import org.ole.planet.myplanet.databinding.FragmentNewsBinding;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmNews;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.FileUtils;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.KeyboardUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.realm.Case;
import io.realm.Sort;

public class NewsFragment extends BaseNewsFragment {
    private FragmentNewsBinding fragmentNewsBinding;
    RealmUserModel user;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        fragmentNewsBinding = FragmentNewsBinding.inflate(inflater, container, false);
        llImage = fragmentNewsBinding.llImages;
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        user = new UserProfileDbHandler(getActivity()).getUserModel();
        KeyboardUtils.setupUI(fragmentNewsBinding.newsFragmentParentLayout, getActivity());
        fragmentNewsBinding.btnAddStory.setOnClickListener(view -> {
            fragmentNewsBinding.llAddNews.setVisibility(fragmentNewsBinding.llAddNews.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            fragmentNewsBinding.btnAddStory.setText(fragmentNewsBinding.llAddNews.getVisibility() == View.VISIBLE ? getString(R.string.hide_add_story) : getString(R.string.add_story));
        });
        if (getArguments().getBoolean("fromLogin")) {
            fragmentNewsBinding.btnAddStory.setVisibility(View.GONE);
            fragmentNewsBinding.llAddNews.setVisibility(View.GONE);
        }
        return fragmentNewsBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setData(getNewsList());
        fragmentNewsBinding.btnSubmit.setOnClickListener(view -> {
            String message = fragmentNewsBinding.etMessage.getText().toString().trim();
            if (message.isEmpty()) {
                fragmentNewsBinding.tlMessage.setError(getString(R.string.please_enter_message));
                return;
            }
            fragmentNewsBinding.etMessage.setText("");
            HashMap<String, String> map = new HashMap<>();
            map.put("message", message);
            map.put("viewInId", user.getPlanetCode() + "@" + user.getParentCode());
            map.put("viewInSection", "community");
            map.put("messageType", "sync");
            map.put("messagePlanetCode", user.getPlanetCode());
            RealmNews n = RealmNews.createNews(map, mRealm, user, imageList);
            imageList.clear();
            llImage.removeAllViews();
            adapterNews.addItem(n);
            setData(getNewsList());
        });
        fragmentNewsBinding.addNewsImage.setOnClickListener(v -> {
            llImage = v.findViewById(R.id.ll_images);
            FileUtils.openOleFolder(this, 100);
        });
        fragmentNewsBinding.addNewsImage.setVisibility(Constants.showBetaFeature(Constants.KEY_NEWSADDIMAGE, getActivity()) ? View.VISIBLE : View.GONE);
    }

    private List<RealmNews> getNewsList() {
        List<RealmNews> allNews = mRealm.where(RealmNews.class).sort("time", Sort.DESCENDING).isEmpty("replyTo").equalTo("docType", "message", Case.INSENSITIVE).findAll();
        List<RealmNews> list = new ArrayList<>();
        for (RealmNews news : allNews) {
            if (!TextUtils.isEmpty(news.getViewableBy()) && news.getViewableBy().equalsIgnoreCase("community")) {
                list.add(news);
                continue;
            }
            if (!TextUtils.isEmpty(news.getViewIn())) {
                JsonArray ar = new Gson().fromJson(news.getViewIn(), JsonArray.class);
                for (JsonElement e : ar) {
                    JsonObject ob = e.getAsJsonObject();
                    if (ob != null && ob.has("_id") && ob.get("_id").getAsString().equalsIgnoreCase(user != null ? user.getPlanetCode() + "@" + user.getParentCode() : "")) {
                        list.add(news);
                    }
                }
            }
        }
        return list;
    }

    public void setData(List<RealmNews> list) {
        changeLayoutManager(getResources().getConfiguration().orientation, fragmentNewsBinding.rvNews);
        List<String> resourceIds = new ArrayList<>();
        for (RealmNews news : list) {
            if (news.getImagesArray().size() > 0) {
                JsonObject ob = news.getImagesArray().get(0).getAsJsonObject();
                String resourceId = JsonUtils.getString("resourceId", ob.getAsJsonObject());
                resourceIds.add(resourceId);
            }
        }
        ArrayList<String> urls = new ArrayList<>();
        SharedPreferences settings = getActivity().getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);
        List<RealmMyLibrary> lib = mRealm.where(RealmMyLibrary.class).in("_id", resourceIds.toArray(new String[]{})).findAll();
        getUrlsAndStartDownload(lib, settings, urls);
        adapterNews = new AdapterNews(getActivity(), list, user, null, true);
        adapterNews.setmRealm(mRealm);
        adapterNews.setFromLogin(getArguments().getBoolean("fromLogin"));
        adapterNews.setListener(this);
        adapterNews.registerAdapterDataObserver(observer);
        fragmentNewsBinding.rvNews.setAdapter(adapterNews);
        showNoData(fragmentNewsBinding.tvMessage, adapterNews.getItemCount());
        fragmentNewsBinding.llAddNews.setVisibility(View.GONE);
        fragmentNewsBinding.btnAddStory.setText(getString(R.string.add_story));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int orientation = newConfig.orientation;
        changeLayoutManager(orientation, fragmentNewsBinding.rvNews);
    }

    final private RecyclerView.AdapterDataObserver observer = new RecyclerView.AdapterDataObserver() {
        @Override
        public void onChanged() {
            showNoData(fragmentNewsBinding.tvMessage, adapterNews.getItemCount());
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            showNoData(fragmentNewsBinding.tvMessage, adapterNews.getItemCount());
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            showNoData(fragmentNewsBinding.tvMessage, adapterNews.getItemCount());
        }
    };
}