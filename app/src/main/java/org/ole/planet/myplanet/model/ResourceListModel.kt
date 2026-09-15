package org.ole.planet.myplanet.model

data class ResourceListModel(
    val library: MyLibrary,
    val item: ResourceItem,
    val tags: List<TagItem>,
    var isOpened: Boolean = false,
    var isLocallyOffline: Boolean = false
)
