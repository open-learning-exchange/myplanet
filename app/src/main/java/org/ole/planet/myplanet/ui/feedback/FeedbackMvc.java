package org.ole.planet.myplanet.ui.feedback;


import android.app.Activity;

import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmFeedback;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;

import java.util.List;

import io.realm.Realm;

public class FeedbackMvc implements FeedbackMvcInterface {

    Realm mRealm;
    DatabaseService databaseService;
    Activity activity_;
    RealmUserModel userModel;

    public FeedbackMvc(Activity activity) {
        this.activity_ = activity;
        databaseService = new DatabaseService(activity);
        mRealm = databaseService.getRealmInstance();
    }

    @Override
    public void setAdapter() {
        userModel = new UserProfileDbHandler(this.activity_).getUserModel();
        List<RealmFeedback> list = mRealm.where(RealmFeedback.class).equalTo("owner", userModel.getName()).findAll();
        if (userModel.isManager())
            list = mRealm.where(RealmFeedback.class).findAll();
        AdapterFeedback adapterFeedback = new AdapterFeedback(this.activity_, list);
        FeedbackListFragment.rvFeedbacks.setAdapter(adapterFeedback);
    }
}
