package org.ole.planet.myplanet.repository

import android.util.Log
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
        val startTime = System.currentTimeMillis()
        Log.d("MyPersonalTiming", "[${startTime}] savePersonalResource started - title: $title")

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

        val beforeSave = System.currentTimeMillis()
        Log.d("MyPersonalTiming", "[${beforeSave}] Object created (+${beforeSave - startTime}ms), calling save()")

        save(personal)

        val afterSave = System.currentTimeMillis()
        Log.d("MyPersonalTiming", "[${afterSave}] save() completed (+${afterSave - beforeSave}ms, total: ${afterSave - startTime}ms)")
    }

    override fun getPersonalResources(userId: String?): Flow<List<RealmMyPersonal>> {
        if (userId.isNullOrBlank()) {
            return flowOf(emptyList())
        }

        return queryListFlow(RealmMyPersonal::class.java) {
            equalTo("userId", userId)
        }
    }

    override suspend fun deletePersonalResource(id: String) {
        delete(RealmMyPersonal::class.java, "_id", id)
        delete(RealmMyPersonal::class.java, "id", id)
    }

    override suspend fun updatePersonalResource(id: String, updater: (RealmMyPersonal) -> Unit) {
        update(RealmMyPersonal::class.java, "_id", id, updater)
        update(RealmMyPersonal::class.java, "id", id, updater)
    }
}
