package org.ole.planet.myplanet.utilities

import androidx.core.graphics.toColorInt
import fisk.chipcloud.ChipCloudConfig

fun getCloudConfig(): ChipCloudConfig {
    return ChipCloudConfig()
        .useInsetPadding(true)
        .checkedChipColor("#e0e0e0".toColorInt())
        .checkedTextColor("#000000".toColorInt())
        .uncheckedChipColor("#e0e0e0".toColorInt())
        .uncheckedTextColor("#000000".toColorInt())
}
