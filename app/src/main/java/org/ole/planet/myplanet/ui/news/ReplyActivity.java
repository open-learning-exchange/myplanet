package org.ole.planet.myplanet.ui.news;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.ActivityReplyBinding;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmNews;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.FileUtils;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;
import java.util.List;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmList;
import io.realm.Sort;

public class ReplyActivity extends AppCompatActivity implements AdapterNews.OnNewsItemClickListener {
    private ActivityReplyBinding activityReplyBinding;
    Realm mRealm;
    String id;
    AdapterNews newsAdapter;
    RealmUserModel user;
    protected RealmList<String> imageList;
    protected LinearLayout llImage;
    private ActivityResultLauncher<Intent> openFolderLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityReplyBinding = ActivityReplyBinding.inflate(getLayoutInflater());
        setContentView(activityReplyBinding.getRoot());
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        mRealm = new DatabaseService(this).getRealmInstance();
        setTitle("Reply");
        imageList = new RealmList<>();
        id = getIntent().getStringExtra("id");
        user = new UserProfileDbHandler(this).getUserModel();
        activityReplyBinding.rvReply.setLayoutManager(new LinearLayoutManager(this));
        activityReplyBinding.rvReply.setNestedScrollingEnabled(false);
        showData(id);
        openFolderLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri url = result.getData().getData();
                handleImageSelection(url);
            }
        });
    }

    private void showData(String id) {
        RealmNews news = mRealm.where(RealmNews.class).equalTo("id", id).findFirst();

        List<RealmNews> list = mRealm.where(RealmNews.class).sort("time", Sort.DESCENDING).equalTo("replyTo", id, Case.INSENSITIVE).findAll();
        newsAdapter = new AdapterNews(this, list, user, news, false);
        newsAdapter.setListener(this);
        newsAdapter.setmRealm(mRealm);
        newsAdapter.setFromLogin(getIntent().getBooleanExtra("fromLogin", false));
        activityReplyBinding.rvReply.setAdapter(newsAdapter);
    }

    @Override
    public void showReply(RealmNews news, boolean fromLogin) {
        startActivity(new Intent(this, ReplyActivity.class).putExtra("id", news.id));
    }

    @Override
    public void addImage(LinearLayout llImage) {
        this.llImage = llImage;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        Uri uri = Uri.parse(Utilities.SD_PATH);
        intent.setDataAndType(uri, "*/*");
        openFolderLauncher.launch(Intent.createChooser(intent, "Open folder"));
    }

    private void handleImageSelection(Uri url) {
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
            e.printStackTrace();
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
        if (item.getItemId() == android.R.id.home) finish();
        return super.onOptionsItemSelected(item);
    }
}
