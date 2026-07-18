package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.model.TagData

interface OnTagClickListener {
    fun onTagClicked(tag: TagEntity)
    fun onParentTagClicked(parent: TagData.Parent) {}
    fun onCheckboxTagSelected(tag: TagEntity) {}
    fun hasChildren(tagId: String?): Boolean { return false }
    @JvmSuppressWildcards
    fun onTagSelected(tag: TagEntity) {}
    @JvmSuppressWildcards
    fun onOkClicked(list: List<TagEntity>?) {}
}
