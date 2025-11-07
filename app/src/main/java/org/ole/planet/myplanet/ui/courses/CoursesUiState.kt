package org.ole.planet.myplanet.ui.courses

import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary

data class CoursesUiState(
    val courses: List<RealmMyCourse?> = emptyList(),
    val ratings: HashMap<String?, JsonObject> = hashMapOf(),
    val progress: HashMap<String?, JsonObject> = hashMapOf(),
    val resources: List<RealmMyLibrary> = emptyList()
)
