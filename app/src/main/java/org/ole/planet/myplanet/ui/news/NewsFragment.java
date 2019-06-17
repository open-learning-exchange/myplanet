package org.ole.planet.myplanet.ui.news;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmNews;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.HashMap;
import java.util.List;

import io.realm.Case;
import io.realm.Realm;
import io.realm.Sort;

public class NewsFragment extends Fragment {


    RecyclerView rvNews;
    EditText etMessage;
    TextInputLayout tlMessage;
    Realm mRealm;
    Button btnSubmit;
    RealmUserModel user;

    public NewsFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_news, container, false);
        rvNews = v.findViewById(R.id.rv_news);
        etMessage = v.findViewById(R.id.et_message);
        tlMessage = v.findViewById(R.id.tl_message);
        btnSubmit = v.findViewById(R.id.btn_submit);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        user = new UserProfileDbHandler(getActivity()).getUserModel();
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        List<RealmNews> list = mRealm.where(RealmNews.class).sort("time", Sort.DESCENDING)
                .equalTo("docType", "message", Case.INSENSITIVE)
                .equalTo("viewableBy", "community", Case.INSENSITIVE)
                .equalTo("replyTo", "", Case.INSENSITIVE)
                .findAll();
        rvNews.setLayoutManager(new LinearLayoutManager(getActivity()));
        AdapterNews adapterNews = new AdapterNews(getActivity(), list, user);
        adapterNews.setmRealm(mRealm);
        rvNews.setAdapter(adapterNews);
        btnSubmit.setOnClickListener(view -> {
            String message = etMessage.getText().toString();
            if (message.isEmpty()) {
                tlMessage.setError("Please enter message");
                return;
            }
            etMessage.setText("");
            HashMap<String, String> map = new HashMap<>();
            map.put("message", message);
            map.put("viewableBy", "community");
            map.put("viewableId", "");
            RealmNews.createNews(map, mRealm, user);
            rvNews.getAdapter().notifyDataSetChanged();
        });
    }
}
