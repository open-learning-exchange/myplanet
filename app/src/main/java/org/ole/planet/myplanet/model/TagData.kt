package org.ole.planet.myplanet.model

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
