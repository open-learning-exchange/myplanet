package org.ole.planet.myplanet.userprofile;

import android.content.Context;
import android.content.SharedPreferences;

import org.lightcouch.CouchDbProperties;
import org.ole.planet.myplanet.Data.realm_UserModel;
import org.ole.planet.myplanet.Data.realm_myLibrary;
import org.ole.planet.myplanet.Data.realm_offlineActivities;
import org.ole.planet.myplanet.Data.realm_resourceActivities;
import org.ole.planet.myplanet.SyncActivity;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Date;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmResults;

public class UserProfileDbHandler {
    public static final String KEY_LOGIN = "login";
    public static final String KEY_LOGOUT = "logout";
    public static final String KEY_RESOURCE_OPEN = "visit";
    public static final String KEY_RESOURCE_DOWNLOAD = "download";
    private SharedPreferences settings;
    private Realm mRealm;
    private CouchDbProperties properties;
    private DatabaseService realmService;
    private String fullName;


    public UserProfileDbHandler(Context context) {
        realmService = new DatabaseService(context);
        settings = context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);
        fullName = Utilities.getUserName(settings);
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
        offlineActivities.set_rev(null);
        offlineActivities.set_id(null);
        offlineActivities.setDescription("Member login on offline application");
        offlineActivities.setLoginTime(new Date().getTime());
        mRealm.commitTransaction();
    }

    public void onLogout() {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        realm_offlineActivities offlineActivities = realm_offlineActivities.getRecentLogin(mRealm);
        if (offlineActivities == null) {
            return;
        }
        offlineActivities.setLogoutTime(new Date().getTime());
        mRealm.commitTransaction();
    }

    public void onDestory() {
        if (mRealm != null)
            mRealm.close();
    }

    private realm_offlineActivities createUser() {
        realm_offlineActivities offlineActivities = mRealm.createObject(realm_offlineActivities.class, UUID.randomUUID().toString());
        realm_UserModel model = getUserModel();
        offlineActivities.setUserId(settings.getString("userId", ""));
        offlineActivities.setUserName(model.getName());
        offlineActivities.setParentCode(model.getParentCode());
        offlineActivities.setCreatedOn(model.getPlanetCode());
        return offlineActivities;
    }

    public Long getLastVisit() {
        return (Long) mRealm.where(realm_offlineActivities.class).max("loginTime");
    }


    public int getOfflineVisits() {
        realm_UserModel m = getUserModel();

        RealmResults<realm_offlineActivities> db_users = mRealm.where(realm_offlineActivities.class)
                .equalTo("userName", m.getName())
                .equalTo("type", KEY_LOGIN)
                .findAll();
        if (!db_users.isEmpty()) {
            return db_users.size();
        } else {
            return 0;
        }
    }

    public void setResourceOpenCount(realm_myLibrary item) {
        mRealm.beginTransaction();
        realm_resourceActivities offlineActivities = mRealm.copyToRealm(createResourceUser());
        offlineActivities.setType(KEY_RESOURCE_OPEN);
        offlineActivities.setTitle(item.getTitle());
        offlineActivities.setResourceId(item.getResource_id());
        offlineActivities.setTime(new Date().getTime());
        mRealm.commitTransaction();
    }


    private realm_resourceActivities createResourceUser() {
        realm_resourceActivities offlineActivities = mRealm.createObject(realm_resourceActivities.class, UUID.randomUUID().toString());
        realm_UserModel model = getUserModel();
        offlineActivities.setUser(model.getName());
        offlineActivities.setParentCode(model.getParentCode());
        offlineActivities.setCreatedOn(model.getPlanetCode());
        return offlineActivities;
    }

    public String getNumberOfResourceOpen() {
        Long count = mRealm.where(realm_resourceActivities.class)
                .equalTo("user", fullName)
                .equalTo("type", KEY_RESOURCE_OPEN)
                .count();
        return count == 0 ? "" : "Resource opened " + count + " times.";
    }

    public String getMaxOpenedResource() {
        RealmResults<realm_resourceActivities> result = mRealm.where(realm_resourceActivities.class)
                .equalTo("user", fullName)
                .equalTo("type", KEY_RESOURCE_OPEN).findAll().where().distinct("resourceId").findAll();
        Long maxCount = 0l;
        String maxOpenedResource = "";
        for (realm_resourceActivities realm_resourceActivities : result) {
            Long count = mRealm.where(realm_resourceActivities.class)
                    .equalTo("user",fullName)
                    .equalTo("type", KEY_RESOURCE_OPEN)
                    .equalTo("resourceId", realm_resourceActivities.getResourceId()).count();
            if (count > maxCount) {
                maxCount = count;
                maxOpenedResource = realm_resourceActivities.getTitle();
            }
        }
        return maxCount == 0 ? "" : maxOpenedResource + " opened " + maxCount + " times";
    }

    public void changeTopbarSetting(boolean o) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();

        getUserModel().setShowTopbar(o);
        mRealm.commitTransaction();
    }
}
