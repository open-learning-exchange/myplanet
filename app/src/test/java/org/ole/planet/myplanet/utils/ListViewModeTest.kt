package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ListViewModeTest {

    @Test
    fun fromPref_null_defaultsToGrid() {
        assertEquals(ListViewMode.GRID, ListViewMode.fromPref(null))
    }

    @Test
    fun fromPref_unknownValue_defaultsToGrid() {
        assertEquals(ListViewMode.GRID, ListViewMode.fromPref("unknown"))
        assertEquals(ListViewMode.GRID, ListViewMode.fromPref(""))
    }

    @Test
    fun fromPref_grid_returnsGrid() {
        assertEquals(ListViewMode.GRID, ListViewMode.fromPref("GRID"))
    }

    @Test
    fun fromPref_list_returnsList() {
        assertEquals(ListViewMode.LIST, ListViewMode.fromPref("LIST"))
    }

    @Test
    fun fromPref_lowercaseList_defaultsToGrid() {
        assertEquals(ListViewMode.GRID, ListViewMode.fromPref("list"))
    }
}
