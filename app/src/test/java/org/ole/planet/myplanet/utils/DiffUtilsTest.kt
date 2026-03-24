package org.ole.planet.myplanet.utils

import androidx.recyclerview.widget.ListUpdateCallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffUtilsTest {

    class TestUpdateCallback : ListUpdateCallback {
        val events = mutableListOf<String>()

        override fun onInserted(position: Int, count: Int) {
            events.add("INSERT $position $count")
        }

        override fun onRemoved(position: Int, count: Int) {
            events.add("REMOVE $position $count")
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            events.add("MOVE $fromPosition $toPosition")
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            events.add("CHANGE $position $count $payload")
        }
    }

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

    @Test
    fun calculateDiff_insertRemoveChange_dispatchesCorrectUpdates() {
        val oldList = listOf("A", "B", "C")
        val newList = listOf("B", "C_modified", "D")

        val result = DiffUtils.calculateDiff(
            oldList,
            newList,
            areItemsTheSame = { oldItem, newItem -> oldItem.first() == newItem.first() },
            areContentsTheSame = { oldItem, newItem -> oldItem == newItem },
            getChangePayload = { oldItem, newItem -> if (oldItem != newItem) "payload" else null }
        )

        val callback = TestUpdateCallback()
        result.dispatchUpdatesTo(callback)

        val expectedEvents = listOf(
            "INSERT 3 1",
            "CHANGE 2 1 payload",
            "REMOVE 0 1"
        )
        assertEquals(expectedEvents, callback.events)
    }

    @Test
    fun calculateDiff_move_dispatchesCorrectUpdates() {
        val oldList = listOf("A", "B", "C")
        val newList = listOf("C", "A", "B")

        val result = DiffUtils.calculateDiff(
            oldList,
            newList,
            areItemsTheSame = { oldItem, newItem -> oldItem == newItem },
            areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
        )

        val callback = TestUpdateCallback()
        result.dispatchUpdatesTo(callback)

        val expectedEvents = listOf(
            "MOVE 2 0"
        )
        assertEquals(expectedEvents, callback.events)
    }

    @Test
    fun calculateDiff_customPayload_propagatesPayload() {
        val oldList = listOf("A1")
        val newList = listOf("A2")

        val result = DiffUtils.calculateDiff(
            oldList,
            newList,
            areItemsTheSame = { oldItem, newItem -> oldItem.first() == newItem.first() },
            areContentsTheSame = { _, _ -> false },
            getChangePayload = { oldItem, newItem -> "${oldItem}->${newItem}" }
        )

        val callback = TestUpdateCallback()
        result.dispatchUpdatesTo(callback)

        val expectedEvents = listOf("CHANGE 0 1 A1->A2")
        assertEquals(expectedEvents, callback.events)
    }

    @Test
    fun calculateDiff_emptyLists_dispatchesNoUpdates() {
        val oldList = emptyList<String>()
        val newList = emptyList<String>()

        val result = DiffUtils.calculateDiff(
            oldList,
            newList,
            areItemsTheSame = { oldItem, newItem -> oldItem == newItem },
            areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
        )

        val callback = TestUpdateCallback()
        result.dispatchUpdatesTo(callback)

        assertTrue(callback.events.isEmpty())
    }

    @Test
    fun calculateDiff_identicalLists_dispatchesNoUpdates() {
        val oldList = listOf("A", "B", "C")
        val newList = listOf("A", "B", "C")

        val result = DiffUtils.calculateDiff(
            oldList,
            newList,
            areItemsTheSame = { oldItem, newItem -> oldItem == newItem },
            areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
        )

        val callback = TestUpdateCallback()
        result.dispatchUpdatesTo(callback)

        assertTrue(callback.events.isEmpty())
    }
}
