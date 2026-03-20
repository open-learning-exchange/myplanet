package org.ole.planet.myplanet.utils

import androidx.recyclerview.widget.ListUpdateCallback
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

    @Test
    fun calculateDiff_identicalLists_noChangesDispatched() {
        val oldList = listOf("a", "b", "c")
        val newList = listOf("a", "b", "c")

        val result = DiffUtils.calculateDiff(
            oldList, newList,
            areItemsTheSame = { old, new -> old == new },
            areContentsTheSame = { old, new -> old == new }
        )

        var updates = 0
        result.dispatchUpdatesTo(object : ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) { updates++ }
            override fun onRemoved(position: Int, count: Int) { updates++ }
            override fun onMoved(fromPosition: Int, toPosition: Int) { updates++ }
            override fun onChanged(position: Int, count: Int, payload: Any?) { updates++ }
        })

        assertEquals(0, updates)
    }

    @Test
    fun calculateDiff_itemInserted_dispatchesInsertion() {
        val oldList = listOf("a", "b")
        val newList = listOf("a", "b", "c")

        val result = DiffUtils.calculateDiff(
            oldList, newList,
            areItemsTheSame = { old, new -> old == new },
            areContentsTheSame = { old, new -> old == new }
        )

        var insertions = 0
        result.dispatchUpdatesTo(object : ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) { insertions++ }
            override fun onRemoved(position: Int, count: Int) {}
            override fun onMoved(fromPosition: Int, toPosition: Int) {}
            override fun onChanged(position: Int, count: Int, payload: Any?) {}
        })

        assertEquals(1, insertions)
    }

    @Test
    fun calculateDiff_itemsSameContentsDifferent_dispatchesChangeWithPayload() {
        val oldList = listOf("a")
        val newList = listOf("A")

        var payloadCalled = false
        val result = DiffUtils.calculateDiff(
            oldList, newList,
            areItemsTheSame = { old, new -> old.equals(new, ignoreCase = true) },
            areContentsTheSame = { old, new -> old == new },
            getChangePayload = { _, _ ->
                payloadCalled = true
                "PAYLOAD"
            }
        )

        var changedCount = 0
        var receivedPayload: Any? = null
        result.dispatchUpdatesTo(object : ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) {}
            override fun onRemoved(position: Int, count: Int) {}
            override fun onMoved(fromPosition: Int, toPosition: Int) {}
            override fun onChanged(position: Int, count: Int, payload: Any?) {
                changedCount++
                receivedPayload = payload
            }
        })

        assertTrue(payloadCalled)
        assertEquals(1, changedCount)
        assertEquals("PAYLOAD", receivedPayload)
    }

    @Test
    fun calculateDiff_swappedItems_dispatchesMove() {
        val oldList = listOf("a", "b")
        val newList = listOf("b", "a")

        val result = DiffUtils.calculateDiff(
            oldList, newList,
            areItemsTheSame = { old, new -> old == new },
            areContentsTheSame = { old, new -> old == new }
        )

        var moves = 0
        var insertions = 0
        var removals = 0
        result.dispatchUpdatesTo(object : ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) { insertions++ }
            override fun onRemoved(position: Int, count: Int) { removals++ }
            override fun onMoved(fromPosition: Int, toPosition: Int) { moves++ }
            override fun onChanged(position: Int, count: Int, payload: Any?) {}
        })

        assertEquals(1, moves)
        assertEquals(0, insertions)
        assertEquals(0, removals)
    }
}
