package org.ole.planet.myplanet.model

data class Transaction(
    val id: String,
    val date: Long?,
    val description: String?,
    val type: String?,
    val amount: Int,
    val balance: Int,
    val imageName: String? = null
) {
    companion object {
        fun calculateTotals(transactions: List<Transaction>): FinanceHeaderState {
            var debit = 0
            var credit = 0
            for (transaction in transactions) {
                if ("credit".equals(transaction.type, ignoreCase = true)) {
                    credit += transaction.amount
                } else {
                    debit += transaction.amount
                }
            }
            val total = credit - debit
            return FinanceHeaderState(
                debit = debit,
                credit = credit,
                total = total,
                isCautionVisible = total < 0
            )
        }
    }
}
