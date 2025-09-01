package org.ole.planet.myplanet.repository

import java.util.Date
import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyPersonal

class MyPersonalRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), MyPersonalRepository {

    override suspend fun savePersonalResource(
        title: String,
        userId: String?,
        userName: String?,
        path: String?,
        description: String?
    ) {
        executeTransaction { realm ->
            val personal = realm.createObject(RealmMyPersonal::class.java, UUID.randomUUID().toString())
            personal.title = title
            personal.userId = userId
            personal.userName = userName
            personal.path = path
            personal.date = Date().time
            personal.description = description
        }
    }
}
