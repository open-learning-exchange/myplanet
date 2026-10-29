package org.ole.planet.myplanet.base

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.flexbox.FlexboxLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BaseDashboardFragmentRenderKeysTest {

    private fun newFlexbox() = FlexboxLayout(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun `first render of a fresh view is never skipped`() {
        assertFalse(newFlexbox().isRenderedWith(listOf("a" to "Title")))
    }

    @Test
    fun `first render of an empty list is not skipped so the placeholder shows`() {
        assertFalse(newFlexbox().isRenderedWith(emptyList()))
    }

    @Test
    fun `same keys from a new emission skip the rebuild`() {
        val flexbox = newFlexbox()
        flexbox.isRenderedWith(listOf("a" to "Title", "b" to "Other"))

        assertTrue(flexbox.isRenderedWith(listOf("a" to "Title", "b" to "Other")))
    }

    @Test
    fun `the same items in a new order skip the rebuild`() {
        val flexbox = newFlexbox()
        flexbox.isRenderedWith(listOf("a" to "Title", "b" to "Other", "c" to "Third"))

        assertTrue(flexbox.isRenderedWith(listOf("c" to "Third", "a" to "Title", "b" to "Other")))
    }

    @Test
    fun `a changed title, added item or removed item rebuilds`() {
        val flexbox = newFlexbox()
        flexbox.isRenderedWith(listOf("a" to "Title", "b" to "Other"))

        assertFalse(flexbox.isRenderedWith(listOf("a" to "Renamed", "b" to "Other")))
        assertFalse(flexbox.isRenderedWith(listOf("a" to "Renamed", "b" to "Other", "c" to "New")))
        assertFalse(flexbox.isRenderedWith(listOf("a" to "Renamed", "c" to "New")))
    }

    @Test
    fun `a duplicate key added or dropped still rebuilds`() {
        val flexbox = newFlexbox()
        flexbox.isRenderedWith(listOf(null to "Same", null to "Same"))

        assertFalse(flexbox.isRenderedWith(listOf(null to "Same")))
    }

    @Test
    fun `going from items to empty rebuilds`() {
        val flexbox = newFlexbox()
        flexbox.isRenderedWith(listOf("a" to "Title"))

        assertFalse(flexbox.isRenderedWith(emptyList()))
    }
}
