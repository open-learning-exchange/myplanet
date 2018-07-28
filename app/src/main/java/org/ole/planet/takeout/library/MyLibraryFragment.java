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

import org.ole.planet.takeout.Data.realm_UserModel;
import org.ole.planet.takeout.Data.realm_myLibrary;
import org.ole.planet.takeout.Data.realm_resources;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.callback.OnLibraryItemSelected;
import org.ole.planet.takeout.datamanager.DatabaseService;
import org.ole.planet.takeout.userprofile.UserProfileDbHandler;
import org.ole.planet.takeout.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmResults;

/**
 * A simple {@link Fragment} subclass.
 */
public class MyLibraryFragment extends Fragment implements OnLibraryItemSelected {

    RecyclerView rvLibrary;
    TextView tvMessage;
    DatabaseService realmService;
    Realm mRealm;
    List<realm_resources> selectedItems;
    List<realm_resources> libraryList;
    TextView tvAddToLib, tvDelete;
    DatabaseService service;

    public MyLibraryFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_my_library, container, false);
        rvLibrary = v.findViewById(R.id.rv_library);
        tvAddToLib = v.findViewById(R.id.tv_add_to_lib);
        tvDelete = v.findViewById(R.id.tv_delete);
        tvMessage = v.findViewById(R.id.tv_message);
        selectedItems = new ArrayList<>();
        libraryList = new ArrayList<>();
        realmService = new DatabaseService(getActivity());
        mRealm = realmService.getRealmInstance();

        changeButtonStatus();
        return v;
    }

    public List<realm_resources> getLibraryList() {
        return mRealm.where(realm_resources.class).findAll();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        rvLibrary.setLayoutManager(new LinearLayoutManager(getActivity()));
        this.libraryList = getLibraryList();
        AdapterLibrary mAdapter = new AdapterLibrary(getActivity(), this.libraryList);
        mAdapter.setListener(this);
        rvLibrary.setAdapter(mAdapter);
        tvAddToLib.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addToMyLibrary();
            }
        });
    }

    private void addToMyLibrary() {
        Utilities.log("Add to library");
        UserProfileDbHandler profileDbHandler = new UserProfileDbHandler(getActivity());
        final realm_UserModel model = mRealm.copyToRealmOrUpdate(profileDbHandler.getUserModel());
        for (int i = 0; i < selectedItems.size(); i++) {
            realm_resources resource = selectedItems.get(i);
            Utilities.log("Add to library " + resource.getTitle());
            realm_myLibrary myLibrary = mRealm.where(realm_myLibrary.class).equalTo("resourceId", resource.getResource_id()).findFirst();
            if (myLibrary == null) {
                realm_myLibrary.createFromResource(resource, mRealm, model.getId());
                Utilities.toast(getActivity(), "Resource Added to my library " + resource.getTitle());
            } else {
                Utilities.toast(getActivity(), "Resource Already Exists in my library : " + resource.getTitle());
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mRealm.close();
    }

    @Override
    public void onSelectedListChange(List<realm_resources> list) {
        this.selectedItems = list;
        changeButtonStatus();
    }

    private void changeButtonStatus() {
        tvDelete.setEnabled(selectedItems.size() > 0);
        tvAddToLib.setEnabled(selectedItems.size() > 0);

    }
}
