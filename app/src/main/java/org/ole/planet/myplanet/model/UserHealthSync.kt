package org.ole.planet.myplanet.model

import androidx.room.ColumnInfo

class UserHealthSync {
    @ColumnInfo(name = "id")
    @JvmField var id: String = ""
    @ColumnInfo(name = "_id")
    @JvmField var _id: String? = null
    @ColumnInfo(name = "planetCode")
    @JvmField var planetCode: String? = null
}
