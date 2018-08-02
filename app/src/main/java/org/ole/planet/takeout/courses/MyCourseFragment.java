package org.ole.planet.takeout.courses;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import org.ole.planet.takeout.Data.realm_courses;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.base.BaseRecyclerFragment;
import org.ole.planet.takeout.callback.OnCourseItemSelected;

import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 */

public class MyCourseFragment extends BaseRecyclerFragment<realm_courses> implements OnCourseItemSelected {

    TextView tvAddToLib, tvDelete, tvSendCourse;

    @Override
    public int getLayout() {
        return R.layout.fragment_my_course;
    }

    @Override
    public RecyclerView.Adapter getAdapter() {
        AdapterCourses mAdapter = new AdapterCourses(getActivity(), getList(realm_courses.class));
        mAdapter.setListener(this);
        return mAdapter;
    }

    public MyCourseFragment() {
    }


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        tvAddToLib = getView().findViewById(R.id.tv_add_to_course);
        tvSendCourse = getView().findViewById(R.id.tv_send_courses);
        tvDelete = getView().findViewById(R.id.tv_delete);
        tvAddToLib.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addToMyList();
            }
        });
    }

    @Override
    public void onSelectedListChange(List<realm_courses> list) {
        this.selectedItems = list;
        changeButtonStatus();
    }

    private void changeButtonStatus() {
        tvDelete.setEnabled(selectedItems.size() > 0);
        tvAddToLib.setEnabled(selectedItems.size() > 0);
        tvSendCourse.setEnabled(selectedItems.size() > 0);
    }
}