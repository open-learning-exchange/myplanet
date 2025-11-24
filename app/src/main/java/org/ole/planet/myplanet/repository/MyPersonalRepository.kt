package org.ole.planet.myplanet.repository

import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmMyPersonal

interface MyPersonalRepository {
    suspend fun savePersonalResource(
        title: String,
        userId: String?,
        userName: String?,
        path: String?,
        description: String?
    )

    suspend fun getPersonalResources(userId: String?): Flow<List<RealmMyPersonal>>

    suspend fun deletePersonalResource(id: String)

    suspend fun updatePersonalResource(id: String, updater: (RealmMyPersonal) -> Unit)
}
