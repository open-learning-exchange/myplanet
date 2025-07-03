package org.ole.planet.myplanet.datamanager

import android.content.Context
import io.realm.Realm
import io.realm.log.LogLevel

class DatabaseService(context: Context) {
    init {
        Realm.init(context)
        RealmLog.setLevel(LogLevel.DEBUG)
        val config = RealmConfiguration.Builder()
            .name(Realm.DEFAULT_REALM_NAME)
            .deleteRealmIfMigrationNeeded()
            .schemaVersion(4)
            .allowWritesOnUiThread(true)
            .build()
        Realm.setDefaultConfiguration(config)
    }

    val realmInstance: Realm
        get() {
            return Realm.getDefaultInstance()
        }
}