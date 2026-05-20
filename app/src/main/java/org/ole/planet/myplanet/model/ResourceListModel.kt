package org.ole.planet.myplanet.model

import com.google.gson.JsonObject

data class ResourceListModel(
    val library: RealmMyLibrary,
    val item: ResourceItem,
    val rating: JsonObject?,
    val tags: List<TagItem>
)
