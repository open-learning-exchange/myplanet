package org.ole.planet.myplanet.callback

import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.TagData

interface OnTagClickListener {
    fun onTagClicked(tag: RealmTag) { /* Default empty implementation */ }
    fun onParentTagClicked(parent: TagData.Parent) { /* Default empty implementation */ }
    fun onCheckboxTagSelected(tag: RealmTag) { /* Default empty implementation */ }
    fun hasChildren(tagId: String?): Boolean { return false }
    @JvmSuppressWildcards
    fun onTagSelected(tag: RealmTag) { /* Default empty implementation */ }
    @JvmSuppressWildcards
    fun onOkClicked(list: List<RealmTag>?) { /* Default empty implementation */ }
}
