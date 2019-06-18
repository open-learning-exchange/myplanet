package org.ole.planet.myplanet.base;

import android.support.v4.app.Fragment;
import android.view.View;
import android.widget.Button;

import org.ole.planet.myplanet.model.RealmNews;
import org.ole.planet.myplanet.ui.news.AdapterNews;

import java.util.List;

import io.realm.Case;
import io.realm.Realm;
import io.realm.Sort;

public abstract class BaseNewsFragment extends Fragment implements AdapterNews.OnNewsItemClickListener {

    public Realm mRealm;
    public Button btnShowMain;


    @Override
    public void showReply(RealmNews news) {
        List<RealmNews> list = mRealm.where(RealmNews.class).sort("time", Sort.DESCENDING)
                .equalTo("replyTo", news.getId(), Case.INSENSITIVE)
                .findAll();
        setData(list);
        btnShowMain.setVisibility(View.VISIBLE);
    }

    public abstract void setData(List<RealmNews> list);
}
