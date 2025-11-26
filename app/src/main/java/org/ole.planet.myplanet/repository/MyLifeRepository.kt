package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyLife

interface MyLifeRepository {
    suspend fun getMyLife(userId: String): List<RealmMyLife>
}
