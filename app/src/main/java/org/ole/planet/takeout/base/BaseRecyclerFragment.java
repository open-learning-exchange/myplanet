package org.ole.planet.takeout.base;

import android.content.SharedPreferences;
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
import org.ole.planet.takeout.Data.realm_myLibrary;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.SyncActivity;
import org.ole.planet.takeout.datamanager.DatabaseService;
import org.ole.planet.takeout.userprofile.UserProfileDbHandler;
import org.ole.planet.takeout.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmObject;

import static android.content.Context.MODE_PRIVATE;

public abstract class BaseRecyclerFragment<LI> extends android.support.v4.app.Fragment {

    public static final String PREFS_NAME = "OLE_PLANET";
    public List<LI> selectedItems;
    RecyclerView recyclerView;
    TextView tvMessage;
    Realm mRealm;
    DatabaseService realmService;
    List<LI> list;
    UserProfileDbHandler profileDbHandler;
    realm_UserModel model;
    public static SharedPreferences settings;

    public BaseRecyclerFragment() {
    }

    public abstract int getLayout();

    public abstract RecyclerView.Adapter getAdapter();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(getLayout(), container, false);
        settings = getActivity().getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        recyclerView = v.findViewById(R.id.recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        tvMessage = v.findViewById(R.id.tv_message);
        selectedItems = new ArrayList<>();
        list = new ArrayList<>();
        realmService = new DatabaseService(getActivity());
        mRealm = realmService.getRealmInstance();
        profileDbHandler = new UserProfileDbHandler(getActivity());
        model = mRealm.copyToRealmOrUpdate(profileDbHandler.getUserModel());
        recyclerView.setAdapter(getAdapter());
        return v;
    }

    public void addToMyList() {
        for (int i = 0; i < selectedItems.size(); i++) {
            RealmObject object = (RealmObject) selectedItems.get(i);
            if (object instanceof realm_myLibrary) {
                realm_myLibrary myObject = mRealm.where(realm_myLibrary.class).equalTo("resourceId", ((realm_myLibrary) object).getResource_id()).findFirst();
                Utilities.log("User id " + model.getId());
                realm_myLibrary.createFromResource(myObject, mRealm, model.getId());
                Utilities.toast(getActivity(), "Added to my library");
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
        String[] mycourseIds = realm_myCourses.getMyCourseIds(mRealm);
        if (c == realm_myLibrary.class) {
            return mRealm.where(c).isEmpty("userId").or()
                .notEqualTo("userId", settings.getString("userId", "--"), Case.INSENSITIVE).findAll();
        } else {
            return mRealm.where(c).not().in("courseId", mycourseIds).findAll();
        }
    }


    public void checkNullAndAdd(RealmObject myObject, RealmObject object, String type) {
        String title = object instanceof realm_courses ? ((realm_courses) object).getCourseTitle() : ((realm_myLibrary) object).getTitle();
        if (myObject != null) {
            Utilities.toast(getActivity(), type + " Already Exists in my " + type + " : " + title);
            return;
        } else if (object instanceof realm_courses) {
            realm_myCourses.createFromCourse((realm_courses) object, mRealm, model.getId());
        }
        Utilities.toast(getActivity(), type + "Added to my " + type);
    }


}