package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.Transaction
import javax.inject.Inject

class TeamMapper @Inject constructor() {

    fun mapTransactionsToPresentationModel(transactions: List<RealmMyTeam>): List<Transaction> {
        val transactionDataList = mutableListOf<Transaction>()
        var balance = 0
        for (team in transactions.filter { it._id != null }) {
            balance += if ("debit".equals(team.type, ignoreCase = true)) {
                -team.amount
            } else {
                team.amount
            }
            transactionDataList.add(
                Transaction(
                    id = team._id!!,
                    date = team.date,
                    description = team.description,
                    type = team.type,
                    amount = team.amount,
                    balance = balance
                )
            )
        }
        return transactionDataList
    }
}
