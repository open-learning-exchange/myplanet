package org.ole.planet.myplanet.repository

import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.Transaction

interface TeamsFinancesRepository {
    suspend fun getTeamTransactionsWithBalance(
        teamId: String, startDate: Long? = null,
        endDate: Long? = null, sortAscending: Boolean = false
    ): Flow<List<Transaction>>
    suspend fun createTransaction(
        teamId: String, type: String, note: String, amount: Int, date: Long,
        parentCode: String?, planetCode: String?,
        imageName: String? = null, imageData: ByteArray? = null
    ): Result<Unit>
}
