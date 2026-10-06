package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Room replacement for the former `News` model (voices/discussion posts).
 *
 * `imageUrls` and `labels` (formerly `RealmList<String>`) are plain `List<String>` stored as JSON
 * via the shared [org.ole.planet.myplanet.data.room.Converters]. Persistence goes through
 * [org.ole.planet.myplanet.data.room.dao.NewsDao]. The class name is kept (`News`) so the
 * large voices UI surface is untouched.
 */
@Entity(tableName = "news", indices = [Index("userId"), Index("replyTo"), Index("_id")])
open class News {
    // @JvmField on id/_id so Room does not see ambiguous getId/get_id accessors.
    @PrimaryKey
    @JvmField
    var id: String = ""
    @JvmField
    var _id: String? = null
    var _rev: String? = null
    var userId: String? = null
    var user: String? = null
    var message: String? = null
    var docType: String? = null
    var viewableBy: String? = null
    var viewableId: String? = null
    var avatar: String? = null
    var replyTo: String? = null
    var userName: String? = null
    var messagePlanetCode: String? = null
    var messageType: String? = null
    var updatedDate: Long = 0
    var time: Long = 0
    var createdOn: String? = null
    var parentCode: String? = null
    var imageUrls: List<String>? = null
    var images: String? = null
    var labels: List<String>? = null
    var viewIn: String? = null
    var newsId: String? = null
    var newsRev: String? = null
    var newsUser: String? = null
    var aiProvider: String? = null
    var newsTitle: String? = null
    var conversations: String? = null
    var newsCreatedDate: Long = 0
    var newsUpdatedDate: Long = 0
    var chat: Boolean = false
    var isEdited: Boolean = false
    var editedTime: Long = 0
    var sharedBy: String? = null

    @Ignore
    var sortDate: Long = 0
    // Typed as Any? to keep the entity Gson-free; NewsJson.kt exposes it as `parsedViewIn: JsonArray?`.
    @Ignore
    var parsedViewInSlot: Any? = null
    @Ignore
    var parsedConversations: List<Conversation>? = null
    // Typed as Any? to keep the entity Gson-free; NewsJson.kt exposes it as `parsedImageUrls: List<JsonObject>?`.
    @Ignore
    var parsedImageUrlsSlot: Any? = null
    @Ignore
    var rawViewIn: String? = null
    @Ignore
    var rawConversations: String? = null
    @Ignore
    var rawImageUrls: List<String>? = null
    @Ignore
    var parsedSharedTeamName: String? = null

    // Parse cache for [images], read and filled by NewsJson.kt's `imagesArray`.
    @Ignore
    @Volatile
    var imagesCache: Any? = null

    fun updateMessage(newMessage: String) {
        this.message = newMessage
        this.isEdited = true
        this.editedTime = Clock.System.now().toEpochMilliseconds()
    }

    companion object {
        fun getViewInJson(map: HashMap<String?, String>): String {
            val viewInArray = buildJsonArray {
                if (!map["viewInId"].isNullOrEmpty()) {
                    add(buildJsonObject {
                        put("_id", map["viewInId"])
                        put("section", map["viewInSection"])
                        put("name", map["name"])
                    })
                }
            }
            return viewInArray.toString()
        }
    }
}
