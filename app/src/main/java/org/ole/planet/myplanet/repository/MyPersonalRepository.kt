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

    suspend fun updatePersonalResource(
        personalId: String,
        title: String?,
        description: String?,
    )

    suspend fun deletePersonalResource(personalId: String)

    fun getPersonalResources(userId: String?): Flow<List<RealmMyPersonal>>
}
