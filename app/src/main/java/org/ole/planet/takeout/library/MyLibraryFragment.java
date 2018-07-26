package org.ole.planet.takeout.library;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.ole.planet.takeout.Data.realm_myLibrary;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.datamanager.RealmService;

import java.util.List;

import io.realm.Realm;
import io.realm.RealmResults;

/**
 * A simple {@link Fragment} subclass.
 */
public class MyLibraryFragment extends Fragment {

    RecyclerView rvLibrary;
    TextView tvMessage;
    RealmService realmService;
    Realm mRealm;
    public MyLibraryFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v =  inflater.inflate(R.layout.fragment_my_library, container, false);
        rvLibrary = v.findViewById(R.id.rv_library);
        tvMessage = v.findViewById(R.id.tv_message);
        realmService = new RealmService(getActivity());
        mRealm = realmService.getInstance();
        return v;
    }

    public List<realm_myLibrary> getLibraryList(){
        return mRealm.where(realm_myLibrary.class).findAll();

    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        rvLibrary.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvLibrary.setAdapter(new AdapterLibrary(getActivity(), getLibraryList()));
    }
}
