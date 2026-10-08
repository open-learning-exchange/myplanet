package org.ole.planet.myplanet.ui.settings

import androidx.annotation.StringRes
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.StorageCategoryType

data class StorageCategory(
    val type: StorageCategoryType,
    @StringRes val nameRes: Int
) {
    val extensions: Set<String> get() = type.extensions
}

/**
 * Display side of [StorageCategoryType]: pairs each category with the string it is labelled with.
 * The scanning rules themselves live in the model enum, so the repository layer never reaches in
 * here for them.
 */
object StorageCategories {

    val all: List<StorageCategory> = StorageCategoryType.entries.map { type ->
        StorageCategory(type, type.nameRes)
    }
}

@get:StringRes
private val StorageCategoryType.nameRes: Int
    get() = when (this) {
        StorageCategoryType.VIDEOS -> R.string.storage_videos
        StorageCategoryType.AUDIO -> R.string.storage_audio
        StorageCategoryType.PDFS -> R.string.storage_pdfs
        StorageCategoryType.IMAGES -> R.string.storage_images
        StorageCategoryType.OTHER -> R.string.storage_other
    }
