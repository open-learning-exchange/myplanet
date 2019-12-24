package org.ole.planet.myplanet.ui.team.teamDiscussion;


import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
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

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmNews;
import org.ole.planet.myplanet.model.RealmTeamNotification;
import org.ole.planet.myplanet.ui.news.AdapterNews;
import org.ole.planet.myplanet.ui.team.BaseTeamFragment;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.FileUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import io.realm.Realm;

import static android.app.Activity.RESULT_OK;

/**
 * A simple {@link Fragment} subclass.
 */
public class DiscussionListFragment extends BaseTeamFragment {

    RecyclerView rvDiscussion;
    TextView tvNodata;
    String imageName = "", imageUrl = "";

    ImageView thumb;

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
        List<RealmNews> realmNewsList = mRealm.where(RealmNews.class).equalTo("viewableBy", "teams").equalTo("viewableId", team.getId()).findAll();
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
        thumb = v.findViewById(R.id.thumb);
        v.findViewById(R.id.add_news_image).setOnClickListener(vi -> FileUtils.openOleFolder(this));
        v.findViewById(R.id.ll_image).setVisibility(Constants.showBetaFeature(Constants.KEY_NEWSADDIMAGE, getActivity()) ? View.VISIBLE : View.GONE);

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
                    map.put("imageUrl", imageUrl);
                    map.put("imageName", imageName);
                    RealmNews.createNews(map, mRealm, user);
                    Utilities.log("discussion created");
                    rvDiscussion.getAdapter().notifyDataSetChanged();
                }).setNegativeButton("Cancel", null).show();
    }

    @Override
    public void setData(List<RealmNews> list) {
        showRecyclerView(list);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            Uri url = null;
            String path = "";
            url = data.getData();
            path = FileUtils.getRealPathFromURI(getActivity(), url);
            if (TextUtils.isEmpty(path)) {
                path = getImagePath(url);
            }
            imageUrl = path;
            imageName = FileUtils.getFileNameFromUrl(path);
            try {
                thumb.setVisibility(View.VISIBLE);
                Glide.with(getActivity())
                        .load(new File(imageUrl))
                        .into(thumb);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    public String getImagePath(Uri uri) {
        Cursor cursor = getContext().getContentResolver().query(uri, null, null, null, null);
        cursor.moveToFirst();
        String document_id = cursor.getString(0);
        document_id = document_id.substring(document_id.lastIndexOf(":") + 1);
        cursor.close();

        cursor = getContext().getContentResolver().query(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                null, MediaStore.Images.Media._ID + " = ? ", new String[]{document_id}, null);
        cursor.moveToFirst();
        String path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
        cursor.close();

        return path;
    }
}
