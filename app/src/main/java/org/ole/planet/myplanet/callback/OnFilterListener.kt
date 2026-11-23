package org.ole.planet.myplanet.callback

interface OnFilterListener {
    fun filter(subjects: MutableSet<String>, languages: MutableSet<String>, mediums: MutableSet<String>, levels: MutableSet<String>): Int

    suspend fun getData(): Map<String, Set<String>>

    fun getSelectedFilter(): Map<String, Set<String>>

    fun getFilteredCount(subjects: Set<String>, languages: Set<String>, mediums: Set<String>, levels: Set<String>): Int

    fun clearAllFilters()
}
