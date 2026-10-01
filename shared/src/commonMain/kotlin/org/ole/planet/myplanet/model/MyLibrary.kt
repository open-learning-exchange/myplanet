package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Room replacement for the former `MyLibrary` model (resources).
 *
 * The multi-valued primitive fields (`userId`, `resourceFor`, `subject`, `level`, `tag`,
 * `languages`, formerly `RealmList<String>`) become `List<String>?` stored as JSON via the shared
 * [org.ole.planet.myplanet.data.room.Converters]. `attachments` (formerly
 * `RealmList<Attachment>`) — a value-object child never queried on its own — becomes an
 * embedded JSON `List<Attachment>`. Shelf membership (`userId` list containment) is queried
 * with `LIKE` on the JSON column (see `MyLibraryDao`). The class name is kept so the wide resources
 * UI/repo surface is untouched. Persistence goes
 * through [org.ole.planet.myplanet.data.room.dao.MyLibraryDao].
 */
@Entity(
    tableName = "my_library",
    indices = [
        Index("_rev"),
        Index("titleNormal"),
        Index("resourceId"),
        Index("isPrivate")
    ]
)
open class MyLibrary {
    @PrimaryKey
    @JvmField
    var id: String = ""
    @JvmField
    var _id: String? = null
    var userId: List<String>? = null
    var resourceRemoteAddress: String? = null
    var resourceLocalAddress: String? = null
    var resourceOffline: Boolean = false
    var resourceId: String? = null
    var _rev: String? = null
    var downloadedRev: String? = null
    var needsOptimization: Boolean = false
    var publisher: String? = null
    var linkToLicense: String? = null
    var addedBy: String? = null
    var uploadDate: String? = null
    var createdDate: Long = 0
    var openWith: String? = null
    var openWhichFile: String? = null
    var articleDate: String? = null
    var kind: String? = null
    var language: String? = null
    var author: String? = null
    var year: String? = null
    var medium: String? = null
    var title: String? = null
    var titleNormal: String? = null
    var averageRating: String? = null
    var filename: String? = null
    var mediaType: String? = null
    var resourceType: String? = null
    var description: String? = null
    var translationAudioPath: String? = null
    var sum: Int = 0
    var timesRated: Int = 0
    var resourceFor: List<String>? = null
    var subject: List<String>? = null
    var level: List<String>? = null
    var tag: List<String>? = null
    var languages: List<String>? = null
    var courseId: String? = null
    var stepId: String? = null
    var isPrivate: Boolean = false
    var privateFor: String? = null
    var attachments: List<Attachment>? = null

    fun setUserId(userId: String?) {
        if (userId.isNullOrBlank()) return
        val current = this.userId?.toMutableList() ?: mutableListOf()
        if (!current.contains(userId)) {
            current.add(userId)
        }
        this.userId = current
    }

    @Ignore
    fun isResourceOffline(): Boolean {
        return resourceOffline && _rev == downloadedRev
    }

    @get:Ignore
    val subjectsAsString: String
        get() = subject?.joinToString(", ") ?: ""

    override fun toString(): String {
        return title ?: ""
    }

    fun removeUserId(id: String?) {
        this.userId = this.userId?.filterNot { it == id }
    }

    companion object {
        fun listToString(list: List<String>?): String {
            return list?.joinToString(", ") ?: ""
        }
    }
}

/**
 * Value-object attachment embedded (as JSON) in [MyLibrary]. Never persisted or queried on
 * its own, so it is a plain class rather than a Room entity.
 */
@Serializable
open class Attachment {
    var id: String? = null
    var name: String? = null
    var contentType: String? = null
    var length: Long = 0
    var digest: String? = null
    var isStub: Boolean = false
    var revpos: Int = 0
}
