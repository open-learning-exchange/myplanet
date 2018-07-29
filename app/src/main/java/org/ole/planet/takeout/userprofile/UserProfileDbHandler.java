package org.ole.planet.takeout.userprofile;

import android.content.Context;
import android.content.SharedPreferences;

import org.lightcouch.CouchDbProperties;
import org.ole.planet.takeout.Data.realm_UserModel;
import org.ole.planet.takeout.Data.realm_offlineActivities;
import org.ole.planet.takeout.SyncActivity;
import org.ole.planet.takeout.datamanager.DatabaseService;
import org.ole.planet.takeout.utilities.Utilities;

import java.util.Date;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmResults;

public class  UserProfileDbHandler {
    private SharedPreferences settings;
    private Realm mRealm;
    private CouchDbProperties properties;
    private DatabaseService realmService;
    private String fullName;
    private static final String KEY_LOGIN = "Login";
    private static final String KEY_LOGOUT = "Logout";
    private static final String KEY_RESOURCE_OPEN = "Resource Open";


    public UserProfileDbHandler(Context context) {
        realmService = new DatabaseService(context);
        settings = context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);
        fullName = Utilities.getFullName(settings);
        mRealm = realmService.getRealmInstance();
    }

    public realm_UserModel getUserModel() {
        return mRealm.where(realm_UserModel.class).equalTo("id", settings.getString("userId", ""))
                .findFirst();
    }

    public void onLogin() {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        realm_offlineActivities offlineActivities = mRealm.copyToRealm(createUser());
        offlineActivities.setType(KEY_LOGIN);
        offlineActivities.setDescription("Member login on offline application");
        offlineActivities.setLoginTime(new Date().getTime());
        mRealm.commitTransaction();
    }

    public void onDestory() {
        if (mRealm != null)
            mRealm.close();
    }

    private realm_offlineActivities createUser() {
        realm_offlineActivities offlineActivities = mRealm.createObject(realm_offlineActivities.class, UUID.randomUUID().toString());
        offlineActivities.setUserId(settings.getString("userId", ""));
        offlineActivities.setUserFullName(fullName);
        return offlineActivities;
    }

    public Long getLastVisit() {
        return (Long) mRealm.where(realm_offlineActivities.class).max("loginTime");
    }


    public int getOfflineVisits() {
        RealmResults<realm_offlineActivities> db_users = mRealm.where(realm_offlineActivities.class)
                .equalTo("userId", settings.getString("userId", ""))
                .equalTo("type", KEY_LOGIN)
                .findAll();
        if (!db_users.isEmpty()) {
            return db_users.size();
        } else {
            return 0;
        }
    }

    public void setResourceOpenCount(String id) {
        mRealm.beginTransaction();
        realm_offlineActivities offlineActivities = mRealm.copyToRealm(createUser());
        offlineActivities.setType(KEY_RESOURCE_OPEN);
        offlineActivities.setDescription(id);
        mRealm.commitTransaction();
    }

    public String getNumberOfResourceOpen() {
        Long count = mRealm.where(realm_offlineActivities.class).equalTo("type", KEY_RESOURCE_OPEN)
                .equalTo("userId", settings.getString("userId", ""))
                .equalTo("type", KEY_RESOURCE_OPEN)
                .count();
        return count == 0 ? "" : "Resource opened " + count + " times.";
    }

    public String getMaxOpenedResource() {
        RealmResults<realm_offlineActivities> result = mRealm.where(realm_offlineActivities.class)
                .equalTo("userId", settings.getString("userId", ""))
                .equalTo("type", KEY_RESOURCE_OPEN).findAll().where().distinct("description").findAll();
        Long maxCount = 0l;
        String maxOpenedResource = "";
        for (realm_offlineActivities realm_offlineActivities : result) {
            Utilities.log("desc " + realm_offlineActivities.getDescription());
            Long count = mRealm.where(realm_offlineActivities.class)
                    .equalTo("userId", settings.getString("userId", ""))
                    .equalTo("type", KEY_RESOURCE_OPEN)
                    .equalTo("description", realm_offlineActivities.getDescription()).count();
            if (count > maxCount) {
                maxCount = count;
                maxOpenedResource = realm_offlineActivities.getDescription();
            }
        }
        return maxCount == 0 ? "" : maxOpenedResource + " opened " + maxCount + " times";
    }

}
