package org.ole.planet.myplanet.repository

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

    override suspend fun updatePersonalResource(
        personalId: String,
        title: String?,
        description: String?,
    ) {
        if (personalId.isBlank()) {
            return
        }

        update(
            RealmMyPersonal::class.java,
            "_id",
            personalId,
        ) { personal ->
            personal.title = title
            personal.description = description
        }
    }

    override suspend fun deletePersonalResource(personalId: String) {
        if (personalId.isBlank()) {
            return
        }

        delete(
            RealmMyPersonal::class.java,
            "_id",
            personalId,
        )
    }

    override fun getPersonalResources(userId: String?): Flow<List<RealmMyPersonal>> {
        if (userId.isNullOrBlank()) {
            return flowOf(emptyList())
        }

        return queryListFlow(RealmMyPersonal::class.java) {
            equalTo("userId", userId)
        }
    }
}
