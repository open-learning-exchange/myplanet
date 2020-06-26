package org.ole.planet.myplanet.datamanager;

import android.content.Context;

import org.ole.planet.myplanet.MainApplication;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.internal.IOException;

public class DatabaseService {

    private Context context;

    public DatabaseService(Context context) {
        this.context = context;
    }

    public Realm getRealmInstance() {

        RealmConfiguration config = new RealmConfiguration.Builder()
                .name(Realm.DEFAULT_REALM_NAME)
                .deleteRealmIfMigrationNeeded()
                .schemaVersion(4)
                .build();
        Realm.setDefaultConfiguration(config);
        return Realm.getInstance(config);
    }

}
