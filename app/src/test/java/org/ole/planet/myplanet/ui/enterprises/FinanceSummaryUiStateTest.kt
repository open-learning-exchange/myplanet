package org.ole.planet.myplanet.ui.enterprises

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.model.Transaction

class FinanceSummaryUiStateTest {

    @Test
    fun `from with empty list returns zero state and caution false`() {
        val result = FinanceSummaryUiState.from(emptyList())

        assertEquals(0.0, result.debit, 0.0)
        assertEquals(0.0, result.credit, 0.0)
        assertEquals(0.0, result.total, 0.0)
        assertFalse(result.isCautionVisible)
    }

    @Test
    fun `from with credit-only transactions computes credit correctly`() {
        val transactions = listOf(
            Transaction("1", 0L, "credit 1", "credit", 100.0, 100.0),
            Transaction("2", 0L, "credit 2", "CREDIT", 250.0, 350.0)
        )

        val result = FinanceSummaryUiState.from(transactions)

        assertEquals(0.0, result.debit, 0.0)
        assertEquals(350.0, result.credit, 0.0)
        assertEquals(350.0, result.total, 0.0)
        assertFalse(result.isCautionVisible)
    }

    @Test
    fun `from with debit-only transactions computes debit and sets caution true`() {
        val transactions = listOf(
            Transaction("1", 0L, "debit 1", "debit", 150.0, -150.0),
            Transaction("2", 0L, "debit 2", "DEBIT", 50.0, -200.0)
        )

        val result = FinanceSummaryUiState.from(transactions)

        assertEquals(200.0, result.debit, 0.0)
        assertEquals(0.0, result.credit, 0.0)
        assertEquals(-200.0, result.total, 0.0)
        assertTrue(result.isCautionVisible)
    }

    @Test
    fun `from with mixed transactions computes totals and sets caution appropriately`() {
        val positiveMixed = listOf(
            Transaction("1", 0L, "credit 1", "credit", 500.0, 500.0),
            Transaction("2", 0L, "debit 1", "debit", 200.0, 300.0),
            Transaction("3", 0L, "debit 2", "debit", 100.0, 200.0)
        )

        val positiveResult = FinanceSummaryUiState.from(positiveMixed)

        assertEquals(300.0, positiveResult.debit, 0.0)
        assertEquals(500.0, positiveResult.credit, 0.0)
        assertEquals(200.0, positiveResult.total, 0.0)
        assertFalse(positiveResult.isCautionVisible)

        val negativeMixed = listOf(
            Transaction("1", 0L, "credit 1", "credit", 100.0, 100.0),
            Transaction("2", 0L, "debit 1", "debit", 300.0, -200.0)
        )

        val negativeResult = FinanceSummaryUiState.from(negativeMixed)

        assertEquals(300.0, negativeResult.debit, 0.0)
        assertEquals(100.0, negativeResult.credit, 0.0)
        assertEquals(-200.0, negativeResult.total, 0.0)
        assertTrue(negativeResult.isCautionVisible)
    }

    @Test
    fun `from keeps decimal amounts`() {
        val transactions = listOf(
            Transaction("1", 0L, "credit 1", "credit", 12.5, 12.5),
            Transaction("2", 0L, "debit 1", "debit", 2.25, 10.25)
        )

        val result = FinanceSummaryUiState.from(transactions)

        assertEquals(2.25, result.debit, 0.0)
        assertEquals(12.5, result.credit, 0.0)
        assertEquals(10.25, result.total, 0.0)
        assertFalse(result.isCautionVisible)
    }
}
