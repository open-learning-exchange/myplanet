package org.ole.planet.myplanet.ui.myPersonals;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyPersonal;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.library.AddResourceFragment;

import java.util.List;

import io.realm.Realm;

/**
 * A simple {@link Fragment} subclass.
 */
public class MyPersonalsFragment extends Fragment {

    RecyclerView rvMyPersonal;
    Realm mRealm;

    public MyPersonalsFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_my_personals, container, false);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        rvMyPersonal = v.findViewById(R.id.rv_mypersonal);
        rvMyPersonal.setLayoutManager(new LinearLayoutManager(getActivity()));
        v.findViewById(R.id.add_my_personal).setOnClickListener(vi->{
            AddResourceFragment f = new AddResourceFragment();
            Bundle b = new Bundle();
            b.putInt("type", 1);
            f.setArguments(b);
            f.show(getChildFragmentManager(), "Add Resource");

        });
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        RealmUserModel model = new UserProfileDbHandler(getActivity()).getUserModel();
        List<RealmMyPersonal> realmMyPersonals = mRealm.where(RealmMyPersonal.class).equalTo("userId", model.getId()).findAll();
        rvMyPersonal.setAdapter(new AdapterMyPersonal(getActivity(), realmMyPersonals));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mRealm != null && !mRealm.isClosed())
            mRealm.close();
    }
}
