package org.ole.planet.myplanet.ui.dashboard

data class DashboardItem(
    val id: String?,
    val title: String?,
    val imageId: String? = null,
    val type: ItemType
)

enum class ItemType {
    LIBRARY, COURSE, MEETUP, LIFE
}
