package org.ole.planet.myplanet.data.room.entity.legacy

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Room row for exam/survey submissions formerly represented by RealmSubmission. */
@Entity(tableName = "submissions", indices = [Index("_id"), Index("_rev"), Index("parentId"), Index("type"), Index("userId"), Index("isUpdated")])
data class RoomSubmissionEntity(
    @PrimaryKey @JvmField val id: String,
    @JvmField val _id: String? = null,
    @JvmField val _rev: String? = null,
    val parentId: String? = null,
    val type: String? = null,
    val userId: String? = null,
    val user: String? = null,
    val startTime: Long = 0,
    val lastUpdateTime: Long = 0,
    val grade: Long = 0,
    val status: String? = null,
    val uploaded: Boolean = false,
    val sender: String? = null,
    val source: String? = null,
    val parentCode: String? = null,
    val parent: String? = null,
    val teamId: String? = null,
    val isUpdated: Boolean = false,
)
