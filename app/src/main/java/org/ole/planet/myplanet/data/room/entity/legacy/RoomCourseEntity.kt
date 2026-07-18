package org.ole.planet.myplanet.data.room.entity.legacy

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Room mirror for courses formerly stored as RealmMyCourse. */
@Entity(tableName = "courses", indices = [Index("courseId"), Index("_id")])
data class RoomCourseEntity(
    @PrimaryKey @JvmField val id: String,
    @JvmField val _id: String? = null,
    @JvmField val _rev: String? = null,
    val courseId: String? = null,
    val courseTitle: String? = null,
    val description: String? = null,
    val userId: List<String>? = null,
    val createdDate: Long = 0,
    val updatedDate: Long = 0,
    val steps: List<String>? = null,
)
