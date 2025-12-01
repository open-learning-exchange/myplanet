package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyLife

interface MyLifeRepository {
    suspend fun getMyLifeItems(userId: String?): List<RealmMyLife>
    suspend fun setUpMyLife(userId: String?)
}
