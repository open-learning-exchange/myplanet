package org.ole.planet.takeout.datamanager;

import android.content.Context;

import io.realm.Realm;
import io.realm.RealmConfiguration;

public class RealmService {

    private Context context;

    public RealmService(Context context) {
        this.context = context;
    }

    public Realm getInstance() {
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
