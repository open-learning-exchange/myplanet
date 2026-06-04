package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.ResourceItem
import org.ole.planet.myplanet.model.TagItem

interface OnLibraryItemSelectedListener {
    @JvmSuppressWildcards
    fun onSelectedListChange(list: List<ResourceItem>)
    @JvmSuppressWildcards
    fun onTagClicked(tag: TagItem)
    fun onResourceClicked(item: ResourceItem)
    fun onShareClicked(item: ResourceItem)
}
