package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.ole.planet.myplanet.model.RealmAchievement
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmSubmitPhotos
import org.ole.planet.myplanet.model.RealmUser

data class UploadedPhotoInfo(val photoId: String, val rev: String, val id: String)

data class ResourceData(
    val libraryId: String?,
    val title: String?,
    val isPrivate: Boolean,
    val privateFor: String?,
    val serialized: JsonObject
)

data class ActivityData(
    val activityId: String?,
    val userId: String?,
    val serialized: JsonObject
)

data class TeamLogData(
    val id: String?,
    val time: Long?,
    val user: String?,
    val type: String?,
    val serialized: JsonObject
)

data class UploadResultDto(
    val id: String?,
    val time: Long?,
    val user: String?,
    val type: String?,
    val _id: String,
    val _rev: String
)

data class NewsUploadData(
    val id: String?,
    val _id: String?,
    val message: String?,
    val imageUrls: List<String>,
    val newsJson: JsonObject
)

data class NewsUpdateData(
    val id: String?,
    val body: JsonObject?,
    val imagesArray: JsonArray
)

interface UploadRepository {
    suspend fun getAchievementsForUpload(): List<RealmAchievement>
    suspend fun getPhotosForUpload(photoIds: Array<String>): List<RealmSubmitPhotos>
    suspend fun getResourcesForUpload(user: RealmUser?): List<ResourceData>
    suspend fun updateResourceAfterUpload(libraryId: String?, rev: String?, id: String?, isPrivate: Boolean, privateFor: String?, title: String?, userPlanetCode: String?, sharedPrefPlanetCode: String?)
    suspend fun getLibraryForUpload(libraryId: String?): RealmMyLibrary?
    suspend fun getActivitiesForUpload(): List<ActivityData>
    suspend fun updateActivitiesAfterUpload(successfulUpdates: Map<String, JsonObject?>)
    suspend fun getTeamLogsForUpload(): List<TeamLogData>
    suspend fun updateTeamLogsAfterUpload(successfulUploads: List<UploadResultDto>)
    suspend fun getNewsForUpload(): List<NewsUploadData>
    suspend fun updateNewsAfterUpload(successfulUpdates: List<NewsUpdateData>)
}
