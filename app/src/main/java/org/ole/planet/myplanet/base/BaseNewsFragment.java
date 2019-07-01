package org.ole.planet.myplanet.base;

import android.content.Context;
import android.content.Intent;
import android.support.v4.app.Fragment;
import android.view.View;
import android.widget.Button;

import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.model.RealmNews;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.news.AdapterNews;
import org.ole.planet.myplanet.ui.news.ReplyActivity;

import java.util.List;

import io.realm.Case;
import io.realm.Realm;
import io.realm.Sort;

public abstract class BaseNewsFragment extends Fragment implements AdapterNews.OnNewsItemClickListener {

    public Realm mRealm;
    public OnHomeItemClickListener homeItemClickListener;
    public UserProfileDbHandler profileDbHandler;

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
}
