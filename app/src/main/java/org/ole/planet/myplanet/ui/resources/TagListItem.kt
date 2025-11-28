package org.ole.planet.myplanet.ui.resources

import org.ole.planet.myplanet.model.RealmTag

sealed class TagListItem {
    data class Parent(val tag: RealmTag, var isExpanded: Boolean = false, val tags: List<RealmTag> = mutableListOf(), var hasChildren: Boolean = false) : TagListItem()
    data class Child(val tag: RealmTag) : TagListItem()
}
