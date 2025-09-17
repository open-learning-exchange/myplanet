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
        val filter = JsonObject().apply {
            val tagsArray = JsonArray()
            tags.forEach { tag ->
                tagsArray.add(tag.name)
            }
            add("tags", tagsArray)
            addProperty("doc.gradeLevel", gradeLevel)
            addProperty("doc.subjectLevel", subjectLevel)
        }

        val activity = RealmSearchActivity(UUID.randomUUID().toString()).apply {
            user = userId ?: ""
            time = Calendar.getInstance().timeInMillis
            createdOn = userPlanetCode ?: ""
            parentCode = userParentCode ?: ""
            text = searchText
            type = "courses"
            this.filter = gson.toJson(filter)
        }

        save(activity)
    }
}
