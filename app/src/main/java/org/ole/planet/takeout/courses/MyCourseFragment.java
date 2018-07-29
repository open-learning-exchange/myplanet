package org.ole.planet.takeout.courses;


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
import org.ole.planet.takeout.Data.realm_myCourses;
import org.ole.planet.takeout.Data.realm_myLibrary;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.callback.OnCourseItemSelected;
import org.ole.planet.takeout.datamanager.DatabaseService;
import org.ole.planet.takeout.library.AdapterLibrary;
import org.ole.planet.takeout.userprofile.UserProfileDbHandler;
import org.ole.planet.takeout.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;

/**
 * A simple {@link Fragment} subclass.
 */

public class MyCourseFragment extends Fragment implements OnCourseItemSelected {

    RecyclerView rvLibrary;
    TextView tvMessage;
    DatabaseService realmService;
    Realm mRealm;
    List<realm_courses> selectedItems;
    List<realm_courses> coursesList;
    TextView tvAddToLib, tvDelete, tvSendCourse;
    DatabaseService service;

    public MyCourseFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_my_course, container, false);
        rvLibrary = v.findViewById(R.id.rv_library);
        tvAddToLib = v.findViewById(R.id.tv_add_to_course);
        tvSendCourse = v.findViewById(R.id.tv_send_courses);
        tvDelete = v.findViewById(R.id.tv_delete);
        tvMessage = v.findViewById(R.id.tv_message);
        selectedItems = new ArrayList<>();
        coursesList = new ArrayList<>();
        realmService = new DatabaseService(getActivity());
        mRealm = realmService.getRealmInstance();

        changeButtonStatus();
        return v;
    }

    public List<realm_courses> getLibraryList() {
        return mRealm.where(realm_courses.class).findAll();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        rvLibrary.setLayoutManager(new LinearLayoutManager(getActivity()));
        this.coursesList = getLibraryList();
        AdapterCourses mAdapter = new AdapterCourses(getActivity(), this.coursesList);
        mAdapter.setListener(this);
        rvLibrary.setAdapter(mAdapter);
        tvAddToLib.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addToMyCourses();
            }
        });
    }

    private void addToMyCourses() {
        UserProfileDbHandler profileDbHandler = new UserProfileDbHandler(getActivity());
        final realm_UserModel model = mRealm.copyToRealmOrUpdate(profileDbHandler.getUserModel());
        for (int i = 0; i < selectedItems.size(); i++) {
            realm_courses course = selectedItems.get(i);
            realm_myCourses myLibrary = mRealm.where(realm_myCourses.class).equalTo("courseId", course.getCourseId()).findFirst();
            if (myLibrary == null) {
                realm_myCourses.createFromCourse(course, mRealm, model.getId());
                Utilities.toast(getActivity(), "Course Added to my courses " + course.getCourseTitle());
            } else {
                Utilities.toast(getActivity(), "Course Already Exists in my courses : " + course.getCourseTitle());
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mRealm.close();
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