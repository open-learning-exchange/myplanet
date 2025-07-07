package org.ole.planet.myplanet.datamanager

import android.content.Context
import io.realm.Realm

/**
 * Singleton provider for Realm instances used across the app.
 * Call [init] once with application context before requesting any Realm.
 */
object RealmProvider {
    /**
     * Initialize Realm configuration if it hasn't been done already.
     */
    fun init(context: Context) {
        if (Realm.getDefaultConfiguration() == null) {
            DatabaseService(context.applicationContext)
        }
    }

    /**
     * Returns a new [Realm] instance using the default configuration.
     */
    fun getRealm(): Realm = Realm.getDefaultInstance()
}
