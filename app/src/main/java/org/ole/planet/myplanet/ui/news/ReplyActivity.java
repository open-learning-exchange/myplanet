package org.ole.planet.myplanet.ui.news;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.MenuItem;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmNews;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;

import io.realm.Case;
import io.realm.Realm;
import io.realm.Sort;

public class ReplyActivity extends AppCompatActivity implements AdapterNews.OnNewsItemClickListener {

    Realm mRealm;
    String id;
    AdapterNews newsAdapter;
    RealmUserModel user;
    RecyclerView rvReply;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reply);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        mRealm = new DatabaseService(this).getRealmInstance();
        setTitle("Reply");
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
        rvReply.setAdapter(newsAdapter);
    }

    @Override
    public void showReply(RealmNews news) {
        startActivity(new Intent(this, ReplyActivity.class).putExtra("id", news.getId()));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home)
            finish();
        return super.onOptionsItemSelected(item);
    }
}
