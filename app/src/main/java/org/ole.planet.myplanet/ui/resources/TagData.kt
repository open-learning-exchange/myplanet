package org.ole.planet.myplanet.ui.resources

import org.ole.planet.myplanet.model.RealmTag

sealed class TagData {
    data class Parent(
        val tag: RealmTag,
        var isExpanded: Boolean = false,
        val isSelected: Boolean = false,
        val isSelectMultiple: Boolean = false,
    ) : TagData()

    data class Child(
        val tag: RealmTag,
        val isSelected: Boolean = false,
        val isSelectMultiple: Boolean = false,
    ) : TagData()
}
