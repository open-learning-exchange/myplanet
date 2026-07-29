package org.ole.planet.myplanet.model

import com.google.gson.JsonObject

data class ResourceListModel(
    val library: MyLibrary,
    val item: ResourceItem,
    val rating: JsonObject?,
    val tags: List<TagItem>,
    var isOpened: Boolean = false,
    var isLocallyOffline: Boolean = false
)
