package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffUtilsTest {

    @Test
    fun areItemsTheSame_delegatesToLambda_returnsTrue() {
        var lambdaCalled = false
        val itemCallback = DiffUtils.itemCallback<String>(
            areItemsTheSame = { oldItem, newItem ->
                lambdaCalled = true
                oldItem == newItem
            },
            areContentsTheSame = { _, _ -> false }
        )

        val result = itemCallback.areItemsTheSame("test", "test")

        assertTrue(lambdaCalled)
        assertTrue(result)
    }

    @Test
    fun areContentsTheSame_delegatesToLambda_returnsFalse() {
        var lambdaCalled = false
        val itemCallback = DiffUtils.itemCallback<String>(
            areItemsTheSame = { _, _ -> true },
            areContentsTheSame = { oldItem, newItem ->
                lambdaCalled = true
                oldItem == newItem
            }
        )

        val result = itemCallback.areContentsTheSame("old", "new")

        assertTrue(lambdaCalled)
        assertFalse(result)
    }

    @Test
    fun getChangePayload_noLambdaSupplied_returnsNull() {
        val itemCallback = DiffUtils.itemCallback<String>(
            areItemsTheSame = { _, _ -> true },
            areContentsTheSame = { _, _ -> true }
        )

        val result = itemCallback.getChangePayload("old", "new")

        assertNull(result)
    }

    @Test
    fun getChangePayload_lambdaSupplied_returnsValue() {
        val expectedPayload = "PayloadData"
        var lambdaCalled = false
        val itemCallback = DiffUtils.itemCallback<String>(
            areItemsTheSame = { _, _ -> true },
            areContentsTheSame = { _, _ -> true },
            getChangePayload = { _, _ ->
                lambdaCalled = true
                expectedPayload
            }
        )

        val result = itemCallback.getChangePayload("old", "new")

        assertTrue(lambdaCalled)
        assertEquals(expectedPayload, result)
    }
}
