package org.ole.planet.myplanet.service;

import android.content.Context;
import android.content.SharedPreferences;

import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmOfflineActivity;
import org.ole.planet.myplanet.model.RealmResourceActivity;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Date;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmResults;

public class UserProfileDbHandler {
    public static final String KEY_LOGIN = "login";
    public static final String KEY_RESOURCE_OPEN = "visit";
    public static final String KEY_RESOURCE_DOWNLOAD = "download";
    private SharedPreferences settings;
    private Realm mRealm;
    private DatabaseService realmService;
    private String fullName;

    public UserProfileDbHandler(Context context) {
        realmService = new DatabaseService(context);
        settings = context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);
        fullName = Utilities.getUserName(settings);
        mRealm = realmService.getRealmInstance();
    }

    public RealmUserModel getUserModel() {
        return mRealm.where(RealmUserModel.class).equalTo("id", settings.getString("userId", "")).findFirst();
    }

    public void onLogin() {
        if (!mRealm.isInTransaction()) mRealm.beginTransaction();
        RealmOfflineActivity offlineActivities = mRealm.copyToRealm(createUser());
        offlineActivities.type = KEY_LOGIN;
        offlineActivities._rev = null;
        offlineActivities._id = null;
        offlineActivities.description = "Member login on offline application";
        offlineActivities.loginTime = new Date().getTime();
        mRealm.commitTransaction();
    }

    public void onLogout() {
        if (!mRealm.isInTransaction()) mRealm.beginTransaction();
        RealmOfflineActivity offlineActivities = RealmOfflineActivity.getRecentLogin(mRealm);
        if (offlineActivities == null) {
            return;
        }
        offlineActivities.logoutTime = new Date().getTime();
        mRealm.commitTransaction();
    }

    public void onDestory() {
        if (mRealm != null && !mRealm.isClosed()) {
            mRealm.close();
        }
    }

    private RealmOfflineActivity createUser() {
        RealmOfflineActivity offlineActivities = mRealm.createObject(RealmOfflineActivity.class, UUID.randomUUID().toString());
        RealmUserModel model = getUserModel();
        offlineActivities.userId = model.id;
        offlineActivities.userName = model.name;
        offlineActivities.parentCode = model.parentCode;
        offlineActivities.createdOn = model.planetCode;
        return offlineActivities;
    }

    public Long getLastVisit() {
        return (Long) mRealm.where(RealmOfflineActivity.class).max("loginTime");
    }


    public int getOfflineVisits() {
        return getOfflineVisits(getUserModel());
    }

    public int getOfflineVisits(RealmUserModel m) {
        RealmResults<RealmOfflineActivity> db_users = mRealm.where(RealmOfflineActivity.class).equalTo("userName", m.name).equalTo("type", KEY_LOGIN).findAll();
        if (!db_users.isEmpty()) {
            return db_users.size();
        } else {
            return 0;
        }
    }

    public void setResourceOpenCount(RealmMyLibrary item) {
        setResourceOpenCount(item, KEY_RESOURCE_OPEN);
    }

    public void setResourceOpenCount(RealmMyLibrary item, String type) {
        RealmUserModel model = getUserModel();
        if (model.id.startsWith("guest")) {
            return;
        }
        if (!mRealm.isInTransaction()) mRealm.beginTransaction();
        RealmResourceActivity offlineActivities = mRealm.copyToRealm(createResourceUser(model));
        offlineActivities.type = type;
        offlineActivities.title = item.title;
        offlineActivities.resourceId = item.resourceId;
        offlineActivities.time = new Date().getTime();
        mRealm.commitTransaction();
        Utilities.log("Set resource open");
    }


    private RealmResourceActivity createResourceUser(RealmUserModel model) {
        RealmResourceActivity offlineActivities = mRealm.createObject(RealmResourceActivity.class, UUID.randomUUID().toString());
        offlineActivities.user = model.name;
        offlineActivities.parentCode = model.parentCode;
        offlineActivities.createdOn = model.planetCode;
        return offlineActivities;
    }

    public String getNumberOfResourceOpen() {
        long count = mRealm.where(RealmResourceActivity.class).equalTo("user", fullName).equalTo("type", KEY_RESOURCE_OPEN).count();
        return count == 0 ? "" : "Resource opened " + count + " times.";
    }

    public String getMaxOpenedResource() {
        RealmResults<RealmResourceActivity> result = mRealm.where(RealmResourceActivity.class).equalTo("user", fullName).equalTo("type", KEY_RESOURCE_OPEN).findAll().where().distinct("resourceId").findAll();
        long maxCount = 0L;
        String maxOpenedResource = "";
        for (RealmResourceActivity realm_resourceActivities : result) {
            long count = mRealm.where(RealmResourceActivity.class).equalTo("user", fullName).equalTo("type", KEY_RESOURCE_OPEN).equalTo("resourceId", realm_resourceActivities.resourceId).count();
            if (count > maxCount) {
                maxCount = count;
                maxOpenedResource = realm_resourceActivities.title;
            }
        }
        return maxCount == 0 ? "" : maxOpenedResource + " opened " + maxCount + " times";
    }

    public void changeTopbarSetting(boolean o) {
        if (!mRealm.isInTransaction()) mRealm.beginTransaction();

        getUserModel().isShowTopbar = o;
        mRealm.commitTransaction();
    }
}
