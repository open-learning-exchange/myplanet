package org.ole.planet.myplanet.model

import androidx.room.ColumnInfo

data class UserHealthSync(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "_id") val _id: String?,
    @ColumnInfo(name = "planetCode") val planetCode: String?
)
