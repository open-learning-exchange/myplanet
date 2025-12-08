package org.ole.planet.myplanet.utilities

import io.realm.Realm
import org.ole.planet.myplanet.BuildConfig

fun logRealmRemoved(realm: Realm, type: String, userId: String, _id: String) {
    if (BuildConfig.FLAVOR != "lite") {
        try {
            val realmRemovedLogClass = Class.forName("org.ole.planet.myplanet.model.RealmRemovedLog")
            val onRemoveMethod = realmRemovedLogClass.getMethod("onRemove", Realm::class.java, String::class.java, String::class.java, String::class.java)
            onRemoveMethod.invoke(null, realm, type, userId, _id)
        } catch (e: Exception) {
            // Class or method not found, ignore in lite flavor
        }
    }
}
