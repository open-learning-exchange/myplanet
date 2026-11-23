package org.ole.planet.myplanet.utils

enum class ListViewMode {
    GRID,
    LIST;

    companion object {
        fun fromPref(value: String?): ListViewMode = if (value == LIST.name) LIST else GRID
    }
}
