package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyPersonal

interface MyPersonalRepository {
    suspend fun savePersonalResource(
        title: String,
        userId: String?,
        userName: String?,
        path: String?,
        description: String?
    )

    suspend fun getMyPersonalByUser(userId: String): List<RealmMyPersonal>
}
