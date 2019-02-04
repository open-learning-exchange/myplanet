package org.ole.planet.myplanet.datamanager;

import android.content.Context;

import org.ole.planet.myplanet.MainApplication;

import io.realm.Realm;
import io.realm.RealmConfiguration;

public class DatabaseService {

    private Context context;

    public DatabaseService(Context context) {
        this.context = context;
    }

    public Realm getRealmInstance() {
        Realm.init(context);
        RealmConfiguration config = new RealmConfiguration.Builder()
                .name(Realm.DEFAULT_REALM_NAME)
                .deleteRealmIfMigrationNeeded()
                .schemaVersion(4)
                .build();

        Realm.setDefaultConfiguration(config);
        return Realm.getInstance(config);
    }

}
