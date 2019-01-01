package org.ole.planet.myplanet.datamanager;

import android.content.Context;

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

//    public CouchDbProperties getClouchDbProperties(String dbName, SharedPreferences settings) {
//        String path = settings.getInt("url_Port", 80) == 80 || settings.getInt("url_Port", 80) == 443 ? "db" : null;
//        return new CouchDbProperties()
//                .setDbName(dbName)
//                .setCreateDbIfNotExist(false)
//                .setProtocol(settings.getString("url_Scheme", "http"))
//                .setHost(settings.getString("url_Host", "192.168.2.1"))
//                .setPort(settings.getInt("url_Port", 80))
//                .setPath(path)
//                .setSocketTimeout(100)
//                .setUsername(settings.getString("url_user", ""))
//                .setPassword(settings.getString("url_pwd", ""))
//                .setMaxConnections(101)
//                .setConnectionTimeout(0);
//    }

}
