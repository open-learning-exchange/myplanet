package org.ole.planet.myplanet.ui.news;

import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import android.net.Uri;
import android.os.Bundle;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmNews;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.FileUtils;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmList;
import io.realm.Sort;

public class ReplyActivity extends AppCompatActivity implements AdapterNews.OnNewsItemClickListener {

    Realm mRealm;
    String id;
    AdapterNews newsAdapter;
    RealmUserModel user;
    RecyclerView rvReply;
    protected RealmList<String> imageList;
    protected LinearLayout llImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reply);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        mRealm = new DatabaseService(this).getRealmInstance();
        setTitle("Reply");
        imageList = new RealmList<>();
        id = getIntent().getStringExtra("id");
        user = new UserProfileDbHandler(this).getUserModel();
        rvReply = findViewById(R.id.rv_reply);
        rvReply.setLayoutManager(new LinearLayoutManager(this));
        rvReply.setNestedScrollingEnabled(false);
        showData(id);
    }

    private void showData(String id) {
        RealmNews news = mRealm.where(RealmNews.class).equalTo("id", id).findFirst();

        List<RealmNews> list = mRealm.where(RealmNews.class).sort("time", Sort.DESCENDING)
                .equalTo("replyTo", id, Case.INSENSITIVE)
                .findAll();
        newsAdapter = new AdapterNews(this, list, user, news);
        newsAdapter.setListener(this);
        newsAdapter.setmRealm(mRealm);
        newsAdapter.setFromLogin(getIntent().getBooleanExtra("fromLogin", false));
        rvReply.setAdapter(newsAdapter);
    }

    @Override
    public void showReply(RealmNews news, boolean fromLogin) {
        startActivity(new Intent(this, ReplyActivity.class).putExtra("id", news.getId()));
    }

    @Override
    public void addImage(LinearLayout llImage) {
        this.llImage = llImage;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        Uri uri = Uri.parse(Utilities.SD_PATH);
        intent.setDataAndType(uri, "*/*");
        startActivityForResult(Intent.createChooser(intent, "Open folder"), 102);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            Uri url = data.getData();
            String path = FileUtils.getRealPathFromURI(this, url);
            if (TextUtils.isEmpty(path)) {
                path = FileUtils.getImagePath(this, url);
            }
            JsonObject object = new JsonObject();
            object.addProperty("imageUrl", path);
            object.addProperty("fileName", FileUtils.getFileNameFromUrl(path));
            imageList.add(new Gson().toJson(object));
            try {
                showSelectedImages();
            } catch (Exception e) {
            }
        }

    }

    private void showSelectedImages() {
        llImage.removeAllViews();
        llImage.setVisibility(View.VISIBLE);
        for (String img : imageList) {
            JsonObject ob = new Gson().fromJson(img, JsonObject.class);
            View inflater = LayoutInflater.from(this).inflate(R.layout.image_thumb, null);
            ImageView imgView = inflater.findViewById(R.id.thumb);
            Glide.with(this).load(new File(JsonUtils.getString("imageUrl", ob))).into(imgView);
            llImage.addView(inflater);
        }
        newsAdapter.setImageList(imageList);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home)
            finish();
        return super.onOptionsItemSelected(item);
    }
}
