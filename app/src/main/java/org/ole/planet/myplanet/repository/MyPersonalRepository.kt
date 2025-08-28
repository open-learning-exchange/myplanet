package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyPersonal

interface MyPersonalRepository {
    suspend fun getAllMyPersonals(): List<RealmMyPersonal>
    suspend fun getMyPersonalById(id: String): RealmMyPersonal?
    suspend fun getMyPersonalsByUserId(userId: String): List<RealmMyPersonal>
    suspend fun saveMyPersonal(personal: RealmMyPersonal)
    suspend fun updateMyPersonal(id: String, updater: (RealmMyPersonal) -> Unit)
    suspend fun deleteMyPersonal(id: String)
}
