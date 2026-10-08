package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionUtilsTest {

    @Test
    fun testHandleCheck_Add() {
        val list = listOf("Item 1", "Item 2", "Item 3")
        val selectedItems = mutableListOf<String?>()

        // Select "Item 2" (index 1)
        SelectionUtils.handleCheck(true, 1, selectedItems, list)

        assertEquals(1, selectedItems.size)
        assertTrue(selectedItems.contains("Item 2"))
    }

    @Test
    fun testHandleCheck_Remove() {
        val list = listOf("Item 1", "Item 2", "Item 3")
        val selectedItems = mutableListOf<String?>("Item 2")

        // Unselect "Item 2" (index 1)
        SelectionUtils.handleCheck(false, 1, selectedItems, list)

        assertTrue(selectedItems.isEmpty())
        assertFalse(selectedItems.contains("Item 2"))
    }

    @Test
    fun testHandleCheck_RemoveNotPresent() {
        val list = listOf("Item 1", "Item 2", "Item 3")
        val selectedItems = mutableListOf<String?>("Item 1")

        // Unselect "Item 2" (index 1) which is not in selectedItems
        SelectionUtils.handleCheck(false, 1, selectedItems, list)

        assertEquals(1, selectedItems.size)
        assertTrue(selectedItems.contains("Item 1"))
        assertFalse(selectedItems.contains("Item 2"))
    }

    @Test
    fun testHandleCheck_NullItems() {
        val list = listOf("Item 1", null, "Item 3")
        val selectedItems = mutableListOf<String?>()

        // Select null item (index 1)
        SelectionUtils.handleCheck(true, 1, selectedItems, list)

        assertEquals(1, selectedItems.size)
        assertTrue(selectedItems.contains(null))

        // Unselect null item
        SelectionUtils.handleCheck(false, 1, selectedItems, list)
        assertTrue(selectedItems.isEmpty())
    }
}
