package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmList
import io.realm.Sort
import org.ole.planet.myplanet.utilities.GsonUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import java.io.File

data class NewsItem(
    val id: String?,
    val message: String?,
    val time: Long,
    val userId: String?,
    val userName: String?,
    val userImage: String?,
    val userFullName: String?,
    val isEdited: Boolean,
    val sharedBy: String?,
    val replyCount: Int,
    val images: List<NewsImage>,
    val labels: List<String>,
    val conversations: String?, // Keeping as JSON string to defer parsing or parse in mapper if needed
    val sharedTeamName: String,
    val isCommunityNews: Boolean,
    val viewIn: String?,
    val newsRev: String?
)

data class NewsImage(
    val path: String?,
    val isGif: Boolean,
    val isLibraryImage: Boolean = false,
    val libraryId: String? = null,
    val resourceLocalAddress: String? = null
)

object NewsMapper {
    fun map(realm: Realm, newsList: List<RealmNews>, currentUser: RealmUserModel?, context: android.content.Context): List<NewsItem> {
        val userCache = mutableMapOf<String, RealmUserModel?>()
        val leadersListRaw = context.getSharedPreferences(org.ole.planet.myplanet.utilities.Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .getString("communityLeaders", "") ?: ""
        val leadersList = RealmUserModel.parseLeadersJson(leadersListRaw)

        return newsList.map { news ->
            val userId = news.userId
            val userModel = if (!userId.isNullOrEmpty()) {
                userCache.getOrPut(userId) {
                    var u = realm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
                    if (u != null) realm.copyFromRealm(u) else null
                }
            } else null

            val currentLeader = if (userModel == null) {
                leadersList.find { it.name == news.userName }
            } else null

            val displayUser = userModel ?: currentLeader

            val replyCount = realm.where(RealmNews::class.java)
                .equalTo("replyTo", news.id, io.realm.Case.INSENSITIVE)
                .count().toInt()

            val images = resolveImages(realm, news, context)

            val sharedTeamName = extractSharedTeamName(news)

            NewsItem(
                id = news.id,
                message = news.message,
                time = news.time,
                userId = news.userId,
                userName = news.userName,
                userImage = displayUser?.userImage,
                userFullName = displayUser?.getFullNameWithMiddleName()?.trim() ?: news.userName,
                isEdited = news.isEdited,
                sharedBy = news.sharedBy,
                replyCount = replyCount,
                images = images,
                labels = news.labels?.toList() ?: emptyList(),
                conversations = news.conversations,
                sharedTeamName = sharedTeamName,
                isCommunityNews = news.isCommunityNews,
                viewIn = news.viewIn,
                newsRev = news.newsRev
            )
        }
    }

    private fun resolveImages(realm: Realm, news: RealmNews, context: android.content.Context): List<NewsImage> {
        val images = mutableListOf<NewsImage>()
        val imageUrls = news.imageUrls

        if (!imageUrls.isNullOrEmpty()) {
            for (imageUrl in imageUrls) {
                try {
                    val imgObject = GsonUtils.gson.fromJson(imageUrl, JsonObject::class.java)
                    val path = JsonUtils.getString("imageUrl", imgObject)
                    if (path.isNullOrEmpty()) continue

                    val file = File(path)
                    if (file.exists()) {
                         images.add(NewsImage(
                             path = path,
                             isGif = path.lowercase().endsWith(".gif")
                         ))
                    } else {
                        // Keep the path even if it doesn't exist? Adapter logic checks existence.
                        // But here we want to avoid file checks in UI.
                        // If file doesn't exist, we probably shouldn't show it or show placeholder.
                        // Adapter logic: if (File(path).exists()) File(path) else path
                        // This implies it might be a URL or a file path.
                        images.add(NewsImage(
                             path = path,
                             isGif = path.lowercase().endsWith(".gif")
                         ))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return images
        }

        val imagesArray = news.imagesArray
        if (imagesArray != null && imagesArray.size() > 0) {
            for (i in 0 until imagesArray.size()) {
                val ob = imagesArray[i]?.asJsonObject
                val resourceId = JsonUtils.getString("resourceId", ob) ?: continue

                val library = realm.where(org.ole.planet.myplanet.model.RealmMyLibrary::class.java)
                    .equalTo("_id", resourceId)
                    .findFirst()

                if (library != null) {
                     val basePath = context.getExternalFilesDir(null)
                     if (basePath != null) {
                         val imageFile = File(basePath, "ole/${library.id}/${library.resourceLocalAddress}")
                         // Check existence here to filter out invalid images?
                         // The adapter checks existence.
                         if (imageFile.exists()) {
                             images.add(NewsImage(
                                 path = imageFile.absolutePath,
                                 isGif = library.resourceLocalAddress?.lowercase()?.endsWith(".gif") == true,
                                 isLibraryImage = true,
                                 libraryId = library.id,
                                 resourceLocalAddress = library.resourceLocalAddress
                             ))
                         }
                     }
                }
            }
        }
        return images
    }

    private fun extractSharedTeamName(news: RealmNews): String {
        if (!news.viewIn.isNullOrEmpty()) {
            try {
                val ar = GsonUtils.gson.fromJson(news.viewIn, JsonArray::class.java)
                if (ar.size() > 1) {
                    val ob = ar[0].asJsonObject
                    if (ob.has("name") && !ob.get("name").isJsonNull) {
                        return ob.get("name").asString
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        return ""
    }
}
