package org.ole.planet.myplanet.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Tag(
    val id: String?,
    val name: String?
) : Parcelable
