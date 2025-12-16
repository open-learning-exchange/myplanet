package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyLife

interface MyLifeRepository {
    suspend fun getMyLifeByUserId(userId: String?): List<RealmMyLife>
}
