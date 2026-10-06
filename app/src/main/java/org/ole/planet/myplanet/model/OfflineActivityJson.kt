package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import org.ole.planet.myplanet.utils.GsonUtils

// App-side Gson helpers for OfflineActivity, kept out of the Room entity.

fun OfflineActivity.changeRev(r: JsonObject?) {
    if (r != null) {
        _rev = GsonUtils.getString("_rev", r)
        _id = GsonUtils.getString("_id", r)
    }
}
