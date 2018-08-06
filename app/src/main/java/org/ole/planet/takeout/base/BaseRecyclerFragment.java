package org.ole.planet.takeout.base;

import android.os.Bundle;
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
import org.ole.planet.takeout.Data.realm_resources;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.datamanager.DatabaseService;
import org.ole.planet.takeout.userprofile.UserProfileDbHandler;
import org.ole.planet.takeout.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmObject;

public abstract class BaseRecyclerFragment<LI> extends android.support.v4.app.Fragment {


    public List<LI> selectedItems;
    RecyclerView recyclerView;
    TextView tvMessage;
    Realm mRealm;
    DatabaseService realmService;
    List<LI> list;

    public BaseRecyclerFragment() {
    }

    public abstract int getLayout();

    public abstract RecyclerView.Adapter getAdapter();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(getLayout(), container, false);
        recyclerView = v.findViewById(R.id.recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        tvMessage = v.findViewById(R.id.tv_message);
        selectedItems = new ArrayList<>();
        list = new ArrayList<>();
        realmService = new DatabaseService(getActivity());
        mRealm = realmService.getRealmInstance();
        recyclerView.setAdapter(getAdapter());
        return v;
    }

    public void addToMyList() {
        for (int i = 0; i < selectedItems.size(); i++) {
            RealmObject object = (RealmObject) selectedItems.get(i);
            if (object instanceof realm_resources) {
                realm_myLibrary myObject = mRealm.where(realm_myLibrary.class).equalTo("resourceId", ((realm_resources) object).getResource_id()).findFirst();
                checkNullAndAdd(myObject, object, "resource");
            } else {
                realm_myCourses myObject = mRealm.where(realm_myCourses.class).equalTo("courseId", ((realm_courses) object).getCourseId()).findFirst();
                checkNullAndAdd(myObject, object, "course");
            }
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        mRealm.close();
    }


    public List<LI> getList(Class c) {
        List<RealmObject> list = mRealm.where(c).findAll();
        String[] myIds = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            myIds[i] = c == realm_resources.class ? ((realm_resources) list.get(i)).getTitle() : ((realm_courses) list.get(i)).getCourseTitle();
        }
        return mRealm.where(c).not().in(c == realm_courses.class ? "courseId" : "resource_id", myIds).findAll();
    }


    public void checkNullAndAdd(RealmObject myObject, RealmObject object, String type) {
        UserProfileDbHandler profileDbHandler = new UserProfileDbHandler(getActivity());
        final realm_UserModel model = mRealm.copyToRealmOrUpdate(profileDbHandler.getUserModel());
        String title = object instanceof realm_courses ? ((realm_courses) object).getCourseTitle() : ((realm_resources) object).getTitle();
        if (myObject != null) {
            Utilities.toast(getActivity(), type + " Already Exists in my " + type + " : " + title);
            return;
        } else if (object instanceof realm_courses) {
            realm_myCourses.createFromCourse((realm_courses) object, mRealm, model.getId());
        } else {
            realm_myLibrary.createFromResource((realm_resources) object, mRealm, model.getId());
        }
        Utilities.toast(getActivity(), type + "Added to my " + type);
    }


}