package org.ole.planet.takeout.library;

import android.content.Context;

import org.lightcouch.CouchDbProperties;
import org.ole.planet.takeout.Data.realm_myLibrary;

import java.util.List;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmResults;

public class LibraryDatamanager {
    Realm mRealm;
    CouchDbProperties properties;
    Context context;

    public LibraryDatamanager(Context context) {
        this.context = context;
        Realm.init(context);
        RealmConfiguration config = new RealmConfiguration.Builder()
                .name(Realm.DEFAULT_REALM_NAME)
                .deleteRealmIfMigrationNeeded()
                .schemaVersion(4)
                .build();
        Realm.setDefaultConfiguration(config);
        mRealm = Realm.getInstance(config);
    }

    public List<realm_myLibrary> getLibraryList() {
        RealmResults<realm_myLibrary> db_myLibrary = mRealm.where(realm_myLibrary.class).findAll();
        return db_myLibrary;
    }


}
