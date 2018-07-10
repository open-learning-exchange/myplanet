package org.ole.planet.takeout.database;

import android.content.Context;

import io.realm.Realm;
import io.realm.RealmConfiguration;

public class RealmDbHandler {

      Realm mRealm;

    public RealmDbHandler(Context context) {
        if (mRealm!=null){
            Realm.init(context);
            RealmConfiguration config = new RealmConfiguration.Builder()
                    .name(Realm.DEFAULT_REALM_NAME)
                    .deleteRealmIfMigrationNeeded()
                    .schemaVersion(4)
                    .build();
            Realm.setDefaultConfiguration(config);
            mRealm = Realm.getInstance(config); }
        }
}
