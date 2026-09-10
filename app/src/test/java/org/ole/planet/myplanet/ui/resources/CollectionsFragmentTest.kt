package org.ole.planet.myplanet.ui.resources

import java.lang.reflect.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.model.TagData
import org.ole.planet.myplanet.model.TagEntity

class CollectionsFragmentTest {

    private fun tag(id: String, name: String, attachedTo: List<String> = emptyList()): TagEntity =
        TagEntity().apply {
            this.id = id
            this.name = name
            this.attachedTo = attachedTo
            this.isAttached = attachedTo.isNotEmpty()
        }

    /** A bare [CollectionsFragment] with its private list/selection state wired up via reflection. */
    private fun newFragment(
        parents: List<TagEntity>,
        childMap: Map<String, List<TagEntity>> = emptyMap(),
        selected: List<TagEntity> = emptyList(),
        selectMultiple: Boolean = false,
    ): CollectionsFragment {
        MainApplication.isCollectionSwitchOn = selectMultiple
        val fragment = CollectionsFragment()
        setField(fragment, "list", parents)
        setField(fragment, "childMap", childMap)
        setField(fragment, "selectedItemsList", ArrayList(selected))
        setField(fragment, "currentTagDataList", emptyList<TagData>())
        return fragment
    }

    private fun buildTagDataList(fragment: CollectionsFragment, parents: List<TagEntity>): List<TagData> {
        val method = CollectionsFragment::class.java.getDeclaredMethod("buildTagDataList", List::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(fragment, parents) as List<TagData>
    }

    private fun setCurrentTagDataList(fragment: CollectionsFragment, value: List<TagData>) {
        setField(fragment, "currentTagDataList", value)
    }

    @Test
    fun `buildTagDataList returns a plain List of parents`() {
        val parents = listOf(tag("p1", "Math"), tag("p2", "Science"))
        val fragment = newFragment(parents)

        val result = buildTagDataList(fragment, parents)

        assertEquals(2, result.size)
        result.forEach { assertTrue(it is TagData.Parent) }
    }

    @Test
    fun `buildTagDataList carries expanded children forward across reassignments`() {
        val parents = listOf(tag("p1", "Math"))
        val childMap = mapOf("p1" to listOf(tag("c1", "Algebra")))
        val fragment = newFragment(parents, childMap)

        // First build: collapsed -> just the parent.
        var result = buildTagDataList(fragment, parents)
        assertEquals(1, result.size)
        val parent = result[0] as TagData.Parent
        assertFalse(parent.isExpanded)

        // Simulate onParentTagClicked: flip expansion and reassign (no toMutableList copy).
        parent.isExpanded = true
        setCurrentTagDataList(fragment, result)
        result = buildTagDataList(fragment, parents)

        assertEquals(2, result.size)
        assertTrue(result[0] is TagData.Parent)
        assertTrue(result[1] is TagData.Child)
        assertEquals("c1", (result[1] as TagData.Child).tag.id)
    }

    @Test
    fun `buildTagDataList reflects selection state from selectedItemsList`() {
        val parents = listOf(tag("p1", "Math"), tag("p2", "Science"))
        val fragment = newFragment(parents, selected = listOf(tag("p2", "Science")))

        val result = buildTagDataList(fragment, parents)

        assertEquals(2, result.size)
        assertFalse((result[0] as TagData.Parent).isSelected)
        assertTrue((result[1] as TagData.Parent).isSelected)
    }

    @Test
    fun `buildTagDataList reflects child selection state correctly`() {
        val parents = listOf(tag("p1", "Math"))
        val child = tag("c1", "Algebra")
        val childMap = mapOf("p1" to listOf(child))
        val fragment = newFragment(parents, childMap, selected = listOf(tag("c1", "Algebra")))

        val initialResult = buildTagDataList(fragment, parents)
        val parent = initialResult[0] as TagData.Parent
        parent.isExpanded = true
        setCurrentTagDataList(fragment, initialResult)

        val result = buildTagDataList(fragment, parents)
        assertEquals(2, result.size)
        assertFalse((result[0] as TagData.Parent).isSelected)
        assertTrue((result[1] as TagData.Child).isSelected)
    }

    @Test
    fun `reconcile tags preserves order and keeps unmatched tags`() {
        val loadedParent = tag("p1", "Math (Loaded)")
        val loadedChild = tag("c1", "Algebra (Loaded)")
        val parents = listOf(loadedParent)
        val childMap = mapOf("p1" to listOf(loadedChild))

        val unmappedSelected = tag("x99", "Unknown")
        val staleChildSelected = tag("c1", "Algebra (Stale)")
        val staleParentSelected = tag("p1", "Math (Stale)")

        val selected = listOf(unmappedSelected, staleChildSelected, staleParentSelected)
        val fragment = newFragment(parents, childMap, selected)

        val getMatchKeyMethod = CollectionsFragment::class.java.getDeclaredMethod("getMatchKey", TagEntity::class.java).apply { isAccessible = true }

        val tagMap = HashMap<String, TagEntity>()
        parents.forEach { tag ->
            (getMatchKeyMethod.invoke(fragment, tag) as? String)?.let { tagMap.putIfAbsent(it, tag) }
        }
        childMap.values.forEach { children ->
            children.forEach { tag ->
                (getMatchKeyMethod.invoke(fragment, tag) as? String)?.let { tagMap.putIfAbsent(it, tag) }
            }
        }

        val reconciled = selected.map { sel ->
            val key = getMatchKeyMethod.invoke(fragment, sel) as? String
            if (key != null) tagMap[key] ?: sel else sel
        }

        assertEquals(3, reconciled.size)
        // Order preserved: [unmapped, c1, p1]
        assertEquals(unmappedSelected, reconciled[0])
        assertEquals(loadedChild, reconciled[1])
        assertEquals(loadedParent, reconciled[2])
    }
}

private fun setField(target: Any, name: String, value: Any) {
    val field = findField(target.javaClass, name)
    field.isAccessible = true
    field.set(target, value)
}

private fun findField(type: Class<*>, name: String): Field {
    var current: Class<*>? = type
    while (current != null) {
        try {
            return current.getDeclaredField(name)
        } catch (e: NoSuchFieldException) {
            current = current.superclass
        }
    }
    throw NoSuchFieldException(name)
}
