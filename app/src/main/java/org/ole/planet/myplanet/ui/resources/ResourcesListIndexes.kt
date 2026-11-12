package org.ole.planet.myplanet.ui.resources

import org.ole.planet.myplanet.model.ResourceListModel
import org.ole.planet.myplanet.model.TagItem

internal data class ResourcesListIndexes(
    val modelsById: Map<String, ResourceListModel>,
    val modelsByLocalAddress: Map<String, ResourceListModel>,
    val tagsById: Map<String, TagItem>
)

internal fun buildResourcesListIndexes(models: List<ResourceListModel>): ResourcesListIndexes {
    val modelsById = LinkedHashMap<String, ResourceListModel>()
    val modelsByLocalAddress = LinkedHashMap<String, ResourceListModel>()
    val tagsById = LinkedHashMap<String, TagItem>()
    for (model in models) {
        model.item.id?.let { modelsById.putIfAbsent(it, model) }
        model.item.resourceLocalAddress?.let { modelsByLocalAddress.putIfAbsent(it, model) }
        for (tag in model.tags) {
            tag.id?.let { tagsById.putIfAbsent(it, tag) }
        }
    }
    return ResourcesListIndexes(modelsById, modelsByLocalAddress, tagsById)
}
