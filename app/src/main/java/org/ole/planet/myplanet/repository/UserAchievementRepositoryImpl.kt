package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.di.RealmDispatcher
import org.ole.planet.myplanet.model.AchievementData
import org.ole.planet.myplanet.model.RealmAchievement
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.utils.JsonUtils

class UserAchievementRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
    @RealmDispatcher realmDispatcher: CoroutineDispatcher,
) : RealmRepository(databaseService, realmDispatcher), UserAchievementRepository {

    override suspend fun initializeAchievement(achievementId: String): RealmAchievement? {
        executeTransaction { transactionRealm ->
            val achievement = transactionRealm.where(RealmAchievement::class.java)
                .equalTo("_id", achievementId)
                .findFirst()

            if (achievement == null) {
                transactionRealm.createObject(RealmAchievement::class.java, achievementId)
            }
        }

        return withRealm { realm ->
            val achievement = realm.where(RealmAchievement::class.java)
                .equalTo("_id", achievementId)
                .findFirst()
            achievement?.let { realm.copyFromRealm(it) }
        }
    }

    override suspend fun updateAchievement(
        achievementId: String,
        header: String,
        goals: String,
        purpose: String,
        sendToNation: String,
        achievements: JsonArray,
        references: JsonArray,
        createdOn: String,
        username: String,
        parentCode: String,
        resumeFileName: String
    ) {
        executeTransaction { transactionRealm ->
            val achievement = transactionRealm.where(RealmAchievement::class.java)
                .equalTo("_id", achievementId)
                .findFirst()
            if (achievement != null) {
                achievement.achievementsHeader = header
                achievement.goals = goals
                achievement.purpose = purpose
                achievement.sendToNation = sendToNation
                achievement.createdOn = createdOn
                achievement.username = username
                achievement.parentCode = parentCode
                achievement.setAchievements(achievements)
                achievement.setReferences(references)
                achievement.resumeFileName = resumeFileName
                achievement.isUpdated = true
            }
        }
    }

    override suspend fun getAchievementData(userId: String, planetCode: String): AchievementData = withRealm { realm ->
        val achievement = realm.where(RealmAchievement::class.java)
            .equalTo("_id", "$userId@$planetCode")
            .findFirst()

        if (achievement != null) {
            val achievementCopy = realm.copyFromRealm(achievement)
            val resourceIds = achievementCopy.achievements?.mapNotNull { json ->
                JsonUtils.gson.fromJson(json, JsonObject::class.java)
                    ?.getAsJsonArray("resources")
                    ?.mapNotNull { it.asJsonObject?.get("_id")?.asString }
            }?.flatten()?.distinct()?.toTypedArray() ?: emptyArray()

            val resources = if (resourceIds.isNotEmpty()) {
                realm.copyFromRealm(
                    realm.where(RealmMyLibrary::class.java)
                        .`in`("id", resourceIds)
                        .findAll()
                )
            } else {
                emptyList()
            }

            AchievementData(
                goals = achievementCopy.goals ?: "",
                purpose = achievementCopy.purpose ?: "",
                achievementsHeader = achievementCopy.achievementsHeader ?: "",
                achievements = achievementCopy.achievements ?: emptyList(),
                achievementResources = resources,
                references = achievementCopy.references ?: emptyList(),
                resumeFileName = achievementCopy.resumeFileName ?: ""
            )
        } else {
            AchievementData()
        }
    }

    override suspend fun getAchievementsForUpload(): List<JsonObject> {
        return queryList(RealmAchievement::class.java) {
            not().beginsWith("_id", "guest")
            equalTo("isUpdated", true)
        }.map { RealmAchievement.serialize(it) }
    }

    override suspend fun markAchievementUploaded(id: String, rev: String?) {
        executeTransaction { transactionRealm ->
            val achievement = transactionRealm.where(RealmAchievement::class.java)
                .equalTo("_id", id)
                .findFirst()
            if (achievement != null) {
                if (!rev.isNullOrEmpty()) achievement._rev = rev
                achievement.isUpdated = false
            }
        }
    }
}
