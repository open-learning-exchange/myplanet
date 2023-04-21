package org.ole.planet.myplanet.domain

import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.domain.models.Leader

interface UsersRepository {
    fun getLeaders(): Flow<List<Leader>>
}