package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmSearchActivity
import org.ole.planet.myplanet.model.RealmTag

class SearchRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    private val gson: Gson
) : RealmRepository(databaseService), SearchRepository {

    override suspend fun saveSearchActivity(
        userId: String?,
        userPlanetCode: String?,
        userParentCode: String?,
        searchText: String,
        tags: List<RealmTag>,
        gradeLevel: String,
        subjectLevel: String
    ) {
        executeTransaction { realm ->
            val activity = realm.createObject(RealmSearchActivity::class.java, UUID.randomUUID().toString())
            activity.user = userId ?: ""
            activity.time = Calendar.getInstance().timeInMillis
            activity.createdOn = userPlanetCode ?: ""
            activity.parentCode = userParentCode ?: ""
            activity.text = searchText
            activity.type = "courses"

            val filter = JsonObject()
            filter.add("tags", tags.map(RealmTag::name).fold(JsonArray()) { array, n -> array.apply { add(n) } })
            filter.addProperty("doc.gradeLevel", gradeLevel)
            filter.addProperty("doc.subjectLevel", subjectLevel)

            activity.filter = gson.toJson(filter)
        }
    }
}
