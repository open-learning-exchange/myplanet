package org.ole.planet.myplanet.datamanager

import android.content.Context
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.log.LogLevel
import io.realm.log.RealmLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DatabaseService(context: Context) {
    init {
        Realm.init(context)
        RealmLog.setLevel(LogLevel.DEBUG)
        val config = RealmConfiguration.Builder()
            .name(Realm.DEFAULT_REALM_NAME)
            .deleteRealmIfMigrationNeeded()
            .schemaVersion(4)
            .build()
        Realm.setDefaultConfiguration(config)
    }

    val realmInstance: Realm
        get() = Realm.getDefaultInstance()

    fun <T> withRealmSync(operation: (Realm) -> T): T {
        return Realm.getDefaultInstance().use { realm ->
            operation(realm)
        }
    }

    suspend fun <T> withRealm(operation: suspend (Realm) -> T): T {
        return withContext(Dispatchers.IO) {
            Realm.getDefaultInstance().use { realm ->
                operation(realm)
            }
        }
    }
}
