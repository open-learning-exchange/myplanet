package org.ole.planet.myplanet.base;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import org.ole.planet.myplanet.Data.realm_UserModel;
import org.ole.planet.myplanet.Data.realm_myCourses;
import org.ole.planet.myplanet.Data.realm_myLibrary;
import org.ole.planet.myplanet.Data.realm_stepExam;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.userprofile.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Utilities;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.RealmResults;

import static android.content.Context.MODE_PRIVATE;

public abstract class BaseRecyclerFragment<LI> extends android.support.v4.app.Fragment {

    public static final String PREFS_NAME = "OLE_PLANET";
    public static SharedPreferences settings;
    public List<LI> selectedItems;
    public Realm mRealm;
    public DatabaseService realmService;
    public UserProfileDbHandler profileDbHandler;
    public realm_UserModel model;
    public RecyclerView recyclerView;
    TextView tvMessage;
    List<LI> list;


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
                realm_myLibrary.createFromResource(myObject, mRealm, model.getId());
                Utilities.toast(getActivity(), "Added to my library");
            } else {
                realm_myCourses myObject = realm_myCourses.getMyCourse(mRealm, ((realm_myCourses) object).getCourseId());
                realm_myCourses.createMyCourse(myObject, mRealm, model.getId());
                Utilities.toast(getActivity(), "Added to my courses");
            }
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        mRealm.close();
    }

    public List<LI> search(String s, Class c) {
        if (s.isEmpty()) {
            return getList(c);
        }
        return mRealm.where(c).isEmpty("userId").or()
                .notEqualTo("userId", model.getId(), Case.INSENSITIVE).contains(c == realm_myLibrary.class ? "title" : "courseTitle", s, Case.INSENSITIVE).findAll();
    }

    public List<realm_myLibrary> filterByTag(String[] tags) {
        List<realm_myLibrary> list = (List<realm_myLibrary>) getList(realm_myLibrary.class);
        if (tags.length == 0) {
            return list;
        }
        RealmList<realm_myLibrary> libraries = new RealmList<>();
        for (realm_myLibrary library : list) {
            String tagAsString = library.getTagAsString();
            if (tagAsString.toLowerCase().contains(getTagsAsString(tags).toLowerCase())) {
                libraries.add(library);
            }
        }
        return libraries;

    }

    public String getTagsAsString(String[] tags) {
        StringBuilder s = new StringBuilder();
        for (String tag : tags) {
            s.append(tag).append(", ");
        }
        return s.toString();
    }

    public List<LI> getList(Class c) {
        if (c == realm_stepExam.class) {
            return mRealm.where(c).equalTo("type", "surveys").findAll();
        } else {
            return mRealm.where(c).isEmpty("userId").or()
                    .notEqualTo("userId", model.getId(), Case.INSENSITIVE).findAll();
        }
    }
}