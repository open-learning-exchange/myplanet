package org.ole.planet.myplanet.datamanager

import android.content.Context
import io.realm.Realm
import io.realm.RealmConfiguration

class DatabaseService(context: Context) {
    init {
        Realm.init(context)
    }

    val realmInstance: Realm
        get() {
            val config = RealmConfiguration.Builder().name(Realm.DEFAULT_REALM_NAME)
                .deleteRealmIfMigrationNeeded().schemaVersion(4).build()
            Realm.setDefaultConfiguration(config)
            return Realm.getInstance(config)
        }
}
