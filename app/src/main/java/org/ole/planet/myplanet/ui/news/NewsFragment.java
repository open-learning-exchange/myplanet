package org.ole.planet.myplanet.ui.news;


import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseNewsFragment;
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
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.realm.Case;
import io.realm.Sort;

public class NewsFragment extends BaseNewsFragment {


    RecyclerView rvNews;
    EditText etMessage;
    TextInputLayout tlMessage;
    Button btnSubmit, btnAddImage, btnAddStory;
    LinearLayout llAddNews;
    RealmUserModel user;
    TextView tvMessage;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_news, container, false);
        rvNews = v.findViewById(R.id.rv_news);
        etMessage = v.findViewById(R.id.et_message);
        tlMessage = v.findViewById(R.id.tl_message);
        btnSubmit = v.findViewById(R.id.btn_submit);
        tvMessage = v.findViewById(R.id.tv_message);
        llImage = v.findViewById(R.id.ll_image);
        llImage = v.findViewById(R.id.ll_images);
        llAddNews = v.findViewById(R.id.ll_add_news);
        btnAddStory = v.findViewById(R.id.btn_add_story);
//        thumb = v.findViewById(R.id.thumb);
        btnAddImage = v.findViewById(R.id.add_news_image);
//        btnShowMain = v.findViewById(R.id.btn_main);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        user = new UserProfileDbHandler(getActivity()).getUserModel();
        KeyboardUtils.setupUI(v.findViewById(R.id.news_fragment_parent_layout), getActivity());
        btnAddStory.setOnClickListener(view -> {

            llAddNews.setVisibility(llAddNews.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            btnAddStory.setText(llAddNews.getVisibility() == View.VISIBLE ? "Hide Add Story" : "Add Story");
        });
        if (getArguments().getBoolean("fromLogin")) {
            btnAddStory.setVisibility(View.GONE);
            llAddNews.setVisibility(View.GONE);
        }
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setData(getNewsList());
        btnSubmit.setOnClickListener(view -> {
            String message = etMessage.getText().toString().trim();
            if (message.isEmpty()) {
                tlMessage.setError("Please enter message");
                return;
            }
            etMessage.setText("");
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
        });
        btnAddImage.setOnClickListener(v -> {
            llImage = v.findViewById(R.id.ll_images);
            FileUtils.openOleFolder(this, 100);
        });
        btnAddImage.setVisibility(Constants.showBetaFeature(Constants.KEY_NEWSADDIMAGE, getActivity()) ? View.VISIBLE : View.GONE);
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
        changeLayoutManager(getResources().getConfiguration().orientation, rvNews);
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
        adapterNews = new AdapterNews(getActivity(), list, user, null);
        adapterNews.setmRealm(mRealm);
        adapterNews.setFromLogin(getArguments().getBoolean("fromLogin"));
        adapterNews.setListener(this);
        adapterNews.registerAdapterDataObserver(observer);
        rvNews.setAdapter(adapterNews);
        showNoData(tvMessage, adapterNews.getItemCount());
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int orientation = newConfig.orientation;
        changeLayoutManager(orientation, rvNews);
    }

    final private RecyclerView.AdapterDataObserver observer = new RecyclerView.AdapterDataObserver() {
        @Override
        public void onChanged() {
            showNoData(tvMessage, adapterNews.getItemCount());
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            showNoData(tvMessage, adapterNews.getItemCount());
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            showNoData(tvMessage, adapterNews.getItemCount());
        }
    };

}