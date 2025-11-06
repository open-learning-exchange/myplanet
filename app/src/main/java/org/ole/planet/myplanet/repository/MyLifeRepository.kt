package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyLife
import kotlinx.coroutines.flow.Flow

interface MyLifeRepository {
    fun getMyLife(): Flow<List<RealmMyLife>>
}
