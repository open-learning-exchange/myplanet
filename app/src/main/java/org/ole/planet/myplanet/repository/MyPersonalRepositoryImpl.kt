package org.ole.planet.myplanet.repository

import io.realm.RealmChangeListener
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyPersonal

class MyPersonalRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService
) : RealmRepository(databaseService), MyPersonalRepository {

    override suspend fun savePersonalResource(
        title: String,
        userId: String?,
        userName: String?,
        path: String?,
        description: String?
    ) {
        val personal = RealmMyPersonal().apply {
            id = UUID.randomUUID().toString()
            _id = id
            this.title = title
            this.userId = userId
            this.userName = userName
            this.path = path
            this.date = Date().time
            this.description = description
        }
        save(personal)
    }

    override fun getPersonalResources(userId: String?): Flow<List<RealmMyPersonal>> =
        callbackFlow {
            val realm = databaseService.realmInstance
            val results =
                realm.where(RealmMyPersonal::class.java)
                    .equalTo("userId", userId)
                    .findAllAsync()

            fun copyAndEmit(collection: io.realm.RealmResults<RealmMyPersonal>) {
                if (!collection.isLoaded || !collection.isValid || realm.isClosed) {
                    return
                }
                val snapshot = collection.createSnapshot()
                val detached = realm.copyFromRealm(snapshot).filter { it.userId == userId }
                trySend(detached)
            }

            val listener =
                RealmChangeListener<io.realm.RealmResults<RealmMyPersonal>> { managed ->
                    copyAndEmit(managed)
                }

            var listenerRegistered = false
            try {
                results.addChangeListener(listener)
                listenerRegistered = true
                copyAndEmit(results)
                awaitClose {
                    if (listenerRegistered && !realm.isClosed && results.isValid) {
                        results.removeChangeListener(listener)
                    }
                    if (!realm.isClosed) {
                        realm.close()
                    }
                }
            } finally {
                if (!listenerRegistered) {
                    if (!realm.isClosed && results.isValid) {
                        results.removeChangeListener(listener)
                    }
                    if (!realm.isClosed) {
                        realm.close()
                    }
                }
            }
        }
}
