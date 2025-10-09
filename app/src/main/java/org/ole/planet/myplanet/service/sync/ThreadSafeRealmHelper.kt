package org.ole.planet.myplanet.service.sync

import io.realm.Realm
import org.ole.planet.myplanet.datamanager.DatabaseService

object ThreadSafeRealmHelper {
    
    private val threadLocalRealm = ThreadLocal<Realm?>()
    
    fun <T> withRealm(databaseService: DatabaseService, operation: (Realm) -> T): T? {
        var realm = threadLocalRealm.get()
        var openedNewRealm = false

        if (realm == null || realm.isClosed) {
            realm = databaseService.realmInstance
            threadLocalRealm.set(realm)
            openedNewRealm = true
        }

        return try {
            if (realm.isClosed) {
                throw IllegalStateException("Realm instance is closed")
            }

            operation(realm)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            if (openedNewRealm) {
                closeThreadRealm()
            }
        }
    }

    fun closeThreadRealm() {
        try {
            val realm = threadLocalRealm.get()
            if (realm != null) {
                if (!realm.isClosed) {
                    realm.close()
                }
            }
        } catch (e: Exception) {
            // Ignore close errors
        } finally {
            threadLocalRealm.remove()
        }
    }
    
    fun closeAllThreadRealms() {
        // This is called during app shutdown
        closeThreadRealm()
    }
}
