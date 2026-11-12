package org.ole.planet.myplanet.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.StorageCategoryType

class StorageCategoriesTest {

    @Test
    fun `all labels the categories in the canonical order`() {
        assertEquals(
            listOf(
                R.string.storage_videos,
                R.string.storage_audio,
                R.string.storage_pdfs,
                R.string.storage_images,
                R.string.storage_other
            ),
            StorageCategories.all.map { it.nameRes }
        )
    }

    @Test
    fun `all mirrors the model taxonomy one entry at a time`() {
        assertEquals(StorageCategoryType.entries.toList(), StorageCategories.all.map { it.type })
        StorageCategories.all.forEach { category ->
            assertEquals(category.type.extensions, category.extensions)
        }
    }
}
