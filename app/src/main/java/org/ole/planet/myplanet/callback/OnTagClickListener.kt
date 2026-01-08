package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.TagData

interface OnTagClickListener {
    fun onTagClicked(tag: RealmTag) {}
    fun onParentTagClicked(parent: TagData.Parent) {}
    fun onCheckboxTagSelected(tag: RealmTag) {}
    fun hasChildren(tagId: String?): Boolean { return false }
    @JvmSuppressWildcards
    fun onTagSelected(tag: RealmTag) {}
    @JvmSuppressWildcards
    fun onOkClicked(list: List<RealmTag>?) {}
}
