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
import org.ole.planet.takeout.Data.realm_courses;
import org.ole.planet.takeout.Data.realm_myLibrary;
import org.ole.planet.takeout.Data.realm_myLibrary;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.callback.OnLibraryItemSelected;
import org.ole.planet.takeout.base.BaseRecyclerFragment;
import org.ole.planet.takeout.courses.AdapterCourses;
import org.ole.planet.takeout.datamanager.DatabaseService;
import org.ole.planet.takeout.userprofile.UserProfileDbHandler;
import org.ole.planet.takeout.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;

/**
 * A simple {@link Fragment} subclass.
 */
public class MyLibraryFragment extends BaseRecyclerFragment<realm_myLibrary> implements OnLibraryItemSelected {

    TextView tvAddToLib, tvDelete;


    @Override
    public int getLayout() {
        return R.layout.fragment_my_library;
    }

    @Override
    public RecyclerView.Adapter getAdapter() {
        AdapterLibrary mAdapter = new AdapterLibrary(getActivity(), getList(realm_myLibrary.class));
        mAdapter.setListener(this);
        return mAdapter;
    }

    public MyLibraryFragment() {
    }


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        tvAddToLib = getView().findViewById(R.id.tv_add_to_lib);
        tvDelete = getView().findViewById(R.id.tv_delete);
        tvAddToLib.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addToMyList();
            }
        });
    }


    @Override
    public void onSelectedListChange(List<realm_myLibrary> list) {
        this.selectedItems = list;
        changeButtonStatus();
    }

    private void changeButtonStatus() {
        tvDelete.setEnabled(selectedItems.size() > 0);
        tvAddToLib.setEnabled(selectedItems.size() > 0);

    }
}
