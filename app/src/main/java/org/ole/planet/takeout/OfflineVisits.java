package org.ole.planet.takeout;

import android.content.SharedPreferences;

import org.ole.planet.takeout.Data.realm_offlineActivities;

import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmResults;

public class OfflineVisits {

    public int offlineVisits(SharedPreferences settings, Realm mRealm, String fullName) {
        //realmConfig("offlineActivities");
        realm_offlineActivities offlineActivities = mRealm.createObject(realm_offlineActivities.class, UUID.randomUUID().toString());
        offlineActivities.setUserId(settings.getString("name", ""));
        offlineActivities.setType("Login");
        offlineActivities.setDescription("Member login on offline application");
        offlineActivities.setUserName(fullName);
        RealmResults<realm_offlineActivities> db_users = mRealm.where(realm_offlineActivities.class)
                .equalTo("userId", settings.getString("name", ""))
                .equalTo("type", "Visits")
                .findAll();
        if (!db_users.isEmpty()) {
            return db_users.size();
        } else {
            return 0;
        }
    }
}