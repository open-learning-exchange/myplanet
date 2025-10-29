package org.ole.planet.myplanet.repository

import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmMyPersonal

interface MyPersonalRepository {
    suspend fun savePersonalResource(
        title: String,
        userId: String?,
        userIdentifier: String?,
        userName: String?,
        path: String?,
        description: String?
    )

    fun getPersonalResources(
        userId: String?,
        userIdentifier: String? = null,
        userName: String? = null
    ): Flow<List<RealmMyPersonal>>

    suspend fun deletePersonalResource(id: String)

    suspend fun updatePersonalResource(id: String, updater: (RealmMyPersonal) -> Unit)
}
