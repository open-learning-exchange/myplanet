package org.ole.planet.myplanet.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionTest {

    @Test
    fun `calculateTotals with empty list returns zero state and caution false`() {
        val result = Transaction.calculateTotals(emptyList())

        assertEquals(0, result.debit)
        assertEquals(0, result.credit)
        assertEquals(0, result.total)
        assertFalse(result.isCautionVisible)
    }

    @Test
    fun `calculateTotals with credit-only transactions computes credit correctly`() {
        val transactions = listOf(
            Transaction("1", 0L, "credit 1", "credit", 100, 100),
            Transaction("2", 0L, "credit 2", "CREDIT", 250, 350)
        )

        val result = Transaction.calculateTotals(transactions)

        assertEquals(0, result.debit)
        assertEquals(350, result.credit)
        assertEquals(350, result.total)
        assertFalse(result.isCautionVisible)
    }

    @Test
    fun `calculateTotals with debit-only transactions computes debit and sets caution true`() {
        val transactions = listOf(
            Transaction("1", 0L, "debit 1", "debit", 150, -150),
            Transaction("2", 0L, "debit 2", "DEBIT", 50, -200)
        )

        val result = Transaction.calculateTotals(transactions)

        assertEquals(200, result.debit)
        assertEquals(0, result.credit)
        assertEquals(-200, result.total)
        assertTrue(result.isCautionVisible)
    }

    @Test
    fun `calculateTotals with mixed transactions computes totals and sets caution appropriately`() {
        val positiveMixed = listOf(
            Transaction("1", 0L, "credit 1", "credit", 500, 500),
            Transaction("2", 0L, "debit 1", "debit", 200, 300),
            Transaction("3", 0L, "debit 2", "debit", 100, 200)
        )

        val positiveResult = Transaction.calculateTotals(positiveMixed)

        assertEquals(300, positiveResult.debit)
        assertEquals(500, positiveResult.credit)
        assertEquals(200, positiveResult.total)
        assertFalse(positiveResult.isCautionVisible)

        val negativeMixed = listOf(
            Transaction("1", 0L, "credit 1", "credit", 100, 100),
            Transaction("2", 0L, "debit 1", "debit", 300, -200)
        )

        val negativeResult = Transaction.calculateTotals(negativeMixed)

        assertEquals(300, negativeResult.debit)
        assertEquals(100, negativeResult.credit)
        assertEquals(-200, negativeResult.total)
        assertTrue(negativeResult.isCautionVisible)
    }
}
