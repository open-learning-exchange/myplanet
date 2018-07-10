package org.ole.planet.takeout.library;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.ole.planet.takeout.R;
import org.ole.planet.takeout.SyncActivity;
import org.ole.planet.takeout.adapter.AdapterResources;

import static io.realm.internal.SyncObjectServerFacade.getApplicationContext;

/**
 * A simple {@link Fragment} subclass.
 */
public class LibraryFragment extends Fragment {

    private static final int PERMISSION_REQUEST_CODE = 111;
    RecyclerView rvLibrary;
    LibraryDatamanager libraryDatamanager;

    public LibraryFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v =  inflater.inflate(R.layout.fragment_library, container, false);
        rvLibrary = v.findViewById(R.id.rv_library);
        if (!checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, getActivity() )) {
            requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, PERMISSION_REQUEST_CODE, getActivity());
        }
        return v;
    }

    public static void requestPermission(String strPermission, int perCode, Activity _a) {


        ActivityCompat.requestPermissions(_a, new String[]{strPermission}, perCode);
    }


    public static boolean checkPermission(String strPermission, Context _c ) {
        int result = ContextCompat.checkSelfPermission(_c, strPermission);
        if (result == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            return false;
        }
    }


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        libraryDatamanager = new LibraryDatamanager(getActivity(), getActivity().getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE));
        rvLibrary.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvLibrary.setAdapter(new AdapterResources(getActivity(), libraryDatamanager.getLibraryList()));
    }
}
