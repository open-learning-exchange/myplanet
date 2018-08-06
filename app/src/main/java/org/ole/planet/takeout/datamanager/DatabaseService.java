package org.ole.planet.takeout.datamanager;

import android.content.Context;
import android.content.SharedPreferences;

import org.lightcouch.CouchDbProperties;

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

    public CouchDbProperties getClouchDbProperties(String dbName, SharedPreferences settings) {
        return new CouchDbProperties()
                .setDbName(dbName)
                .setCreateDbIfNotExist(false)
                .setProtocol(settings.getString("url_Scheme", "http"))
                .setHost(settings.getString("url_Host", "192.168.2.1"))
                .setPort(settings.getInt("url_Port", 3000))
                .setUsername(settings.getString("url_user", ""))
                .setPassword(settings.getString("url_pwd", ""))
                .setMaxConnections(100)
                .setConnectionTimeout(0);
    }


}
