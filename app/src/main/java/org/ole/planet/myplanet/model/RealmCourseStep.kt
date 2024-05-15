package org.ole.planet.myplanet.model

import android.util.Base64
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.Utilities
import java.util.regex.Pattern

open class RealmCourseStep : RealmObject() {
    @JvmField
    @PrimaryKey
    var id: String? = null
    @JvmField
    var courseId: String? = null
    @JvmField
    var stepTitle: String? = null
    @JvmField
    var description: String? = null
    @JvmField
    var noOfResources: Int? = null
}