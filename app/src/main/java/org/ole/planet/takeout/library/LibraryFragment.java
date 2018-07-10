package org.ole.planet.takeout.library;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.ole.planet.takeout.R;
import org.ole.planet.takeout.adapter.AdapterResources;

/**
 * A simple {@link Fragment} subclass.
 */
public class LibraryFragment extends Fragment {

    RecyclerView rvLibrary;
    LibraryDatamanager libraryDatamanager;

    public LibraryFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v =  inflater.inflate(R.layout.fragment_library, container, false);
        rvLibrary = v.findViewById(R.id.rv_library);
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        libraryDatamanager = new LibraryDatamanager(getActivity());
        rvLibrary.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvLibrary.setAdapter(new AdapterResources(getActivity(), libraryDatamanager.getLibraryList()));
    }
}
