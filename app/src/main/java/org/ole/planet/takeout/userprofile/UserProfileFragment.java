package org.ole.planet.takeout.userprofile;


import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.ole.planet.takeout.Data.realm_UserModel;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.datamanager.RealmService;
import org.ole.planet.takeout.utilities.Utilities;

import io.realm.Realm;

public class UserProfileFragment extends Fragment {

    UserProfileDbHandler handler;
    RealmService realmService;
    Realm mRealm;

    public UserProfileFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_user_profile, container, false);
        handler = new UserProfileDbHandler(getActivity());
        realmService = new RealmService(getActivity());
        mRealm = realmService.getInstance();
        populateUserData(v);
        return v;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void populateUserData(View v) {
        realm_UserModel model = mRealm.copyToRealmOrUpdate(handler.getUserModel());
        ((TextView) v.findViewById(R.id.txt_name)).setText(String.format("%s %s %s", model.getFirstName(), model.getMiddleName(), model.getLastName()));
        ((TextView) v.findViewById(R.id.txt_email)).setText(Utilities.checkNA(model.getEmail()));
        ((TextView) v.findViewById(R.id.txt_dob)).setText(Utilities.checkNA(model.getDob()));
        ((TextView) v.findViewById(R.id.txt_community_name)).setText(Utilities.checkNA(model.getCommunityName()));
        ((TextView) v.findViewById(R.id.txt_last_visit)).setText(String.format("Last Login : %s", Utilities.getRelativeTime(handler.getLastVisit())));
        ((TextView) v.findViewById(R.id.txt_visit_count)).setText(String.format("Total Visits : %d", handler.getOfflineVisits()));
        ((TextView) v.findViewById(R.id.txt_max_opened_resource)).setText(Utilities.checkNA(handler.getMaxOpenedResource()));
        ((TextView) v.findViewById(R.id.txt_number_resource_open)).setText(Utilities.checkNA(handler.getNumberOfResourceOpen()));
    }

}
