package org.ole.planet.takeout.courses;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import org.ole.planet.takeout.Data.realm_myCourses;
import org.ole.planet.takeout.Data.realm_myLibrary;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.base.BaseRecyclerFragment;
import org.ole.planet.takeout.callback.OnCourseItemSelected;
import org.ole.planet.takeout.library.AdapterLibrary;
import org.ole.planet.takeout.utilities.Utilities;

import java.util.List;

import io.realm.RealmResults;

/**
 * A simple {@link Fragment} subclass.
 */

public class MyCourseFragment extends BaseRecyclerFragment<realm_myCourses> implements OnCourseItemSelected {

    TextView tvAddToLib;

    EditText etSearch;
    ImageView imgSearch;
    AdapterCourses adapterCourses;

    public MyCourseFragment() {
    }

    @Override
    public int getLayout() {
        return R.layout.fragment_my_course;
    }

    @Override
    public RecyclerView.Adapter getAdapter() {
        adapterCourses = new AdapterCourses(getActivity(), getList(realm_myCourses.class));
        adapterCourses.setListener(this);
        return adapterCourses;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        tvAddToLib = getView().findViewById(R.id.tv_add_to_course);
        tvAddToLib.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addToMyList();
            }
        });
        etSearch = getView().findViewById(R.id.et_search);
        imgSearch = getView().findViewById(R.id.img_search);
        imgSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                adapterCourses.setCourseList(search(etSearch.getText().toString(), realm_myCourses.class));
            }
        });
    }

    @Override
    public void onSelectedListChange(List<realm_myCourses> list) {
        this.selectedItems = list;
        changeButtonStatus();
    }

    private void changeButtonStatus() {
        tvAddToLib.setEnabled(selectedItems.size() > 0);
    }
}