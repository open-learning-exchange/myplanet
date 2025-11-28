package org.ole.planet.myplanet.datamanager

import io.realm.DynamicRealm
import io.realm.RealmMigration
import android.util.Log

class AppRealmMigration : RealmMigration {
    override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {
        val startTime = System.currentTimeMillis()
        val schema = realm.schema
        var currentVersion = oldVersion

        // Example migration block:
        // if (currentVersion == 0L) {
        //     schema.get("User")
        //         ?.addField("last_name", String::class.java)
        //     currentVersion++
        // }

        val endTime = System.currentTimeMillis()
        Log.i("RealmMigration", "Migration completed in ${endTime - startTime}ms")
    }
}
