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
