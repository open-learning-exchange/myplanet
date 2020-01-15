package org.ole.planet.myplanet.base;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.model.RealmNews;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.news.AdapterNews;
import org.ole.planet.myplanet.ui.news.ReplyActivity;
import org.ole.planet.myplanet.utilities.FileUtils;

import java.io.File;
import java.util.List;

import io.realm.Case;
import io.realm.Realm;
import io.realm.Sort;

import static android.app.Activity.RESULT_OK;

public abstract class BaseNewsFragment extends BaseContainerFragment implements AdapterNews.OnNewsItemClickListener {

    public Realm mRealm;
    public OnHomeItemClickListener homeItemClickListener;
    public UserProfileDbHandler profileDbHandler;
    protected String imageName = "", imageUrl = "";
    protected ImageView thumb;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        profileDbHandler = new UserProfileDbHandler(getActivity());
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnHomeItemClickListener)
            homeItemClickListener = (OnHomeItemClickListener) context;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (profileDbHandler != null)
            profileDbHandler.onDestory();
    }

    @Override
    public void showReply(RealmNews news) {
        startActivity(new Intent(getActivity(), ReplyActivity.class).putExtra("id", news.getId()));
    }



    public abstract void setData(List<RealmNews> list);

    public void showNoData(View v, int count) {
        BaseRecyclerFragment.showNoData(v, count);
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        thumb = getView().findViewById(R.id.thumb);
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


    public void changeLayoutManager(int orientation, RecyclerView recyclerView) {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            recyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 2));
        } else {
            recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        }
    }




}
