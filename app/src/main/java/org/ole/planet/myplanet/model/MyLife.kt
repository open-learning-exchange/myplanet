package org.ole.planet.myplanet.model

import android.content.Context
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import org.ole.planet.myplanet.R

/**
 * Room replacement for the former Realm `MyLife` model.
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

    companion object {
        fun defaultItems(context: Context, userId: String?): List<MyLife> = listOf(
            MyLife("ic_myhealth", userId, context.getString(R.string.myhealth)),
            MyLife("my_achievement", userId, context.getString(R.string.achievements)),
            MyLife("ic_submissions", userId, context.getString(R.string.submission)),
            MyLife("ic_my_survey", userId, context.getString(R.string.my_survey)),
            MyLife("ic_references", userId, context.getString(R.string.references)),
            MyLife("ic_calendar", userId, context.getString(R.string.calendar)),
            MyLife("ic_mypersonals", userId, context.getString(R.string.mypersonals))
        )
    }
}
