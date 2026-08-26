package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import org.ole.planet.myplanet.R

/**
 * Room replacement for the former `MyLife` model.
 *
 * The class name is kept so the UI (which uses it purely as a detached data holder) is unaffected
 * by the migration. Persistence now goes through [org.ole.planet.myplanet.data.room.dao.MyLifeDao].
 */
@Entity(tableName = "my_life", indices = [Index("userId")])
class MyLife {
    @PrimaryKey
    var _id: String = ""
    var imageId: String? = null
    var userId: String? = null
    var title: String? = null
    var isVisible: Boolean = false
    var weight: Int = 0

    constructor()

    @Ignore
    constructor(imageId: String?, userId: String?, title: String?) {
        this.imageId = imageId
        this.userId = userId
        this.title = title
        isVisible = true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MyLife) return false
        return _id == other._id &&
            imageId == other.imageId &&
            userId == other.userId &&
            title == other.title &&
            isVisible == other.isVisible &&
            weight == other.weight
    }

    override fun hashCode(): Int {
        var result = _id.hashCode()
        result = 31 * result + (imageId?.hashCode() ?: 0)
        result = 31 * result + (userId?.hashCode() ?: 0)
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + isVisible.hashCode()
        result = 31 * result + weight
        return result
    }

    companion object {
        private val defaultItemPairs = listOf(
            "ic_myhealth" to R.string.myhealth,
            "my_achievement" to R.string.achievements,
            "ic_submissions" to R.string.submission,
            "ic_my_survey" to R.string.my_survey,
            "ic_references" to R.string.references,
            "ic_calendar" to R.string.calendar,
            "ic_mypersonals" to R.string.mypersonals
        )

        fun defaultItems(userId: String?, resolveLabel: (Int) -> String): List<MyLife> =
            defaultItemPairs.map { (imageId, stringRes) ->
                MyLife(imageId, userId, resolveLabel(stringRes))
            }
    }
}
