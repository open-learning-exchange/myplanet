package org.ole.planet.myplanet.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "courses", indices = [Index("courseId"), Index("_id"), Index("courseTitleNormal"), Index("gradeLevel"), Index("subjectLevel")])
open class MyCourse(
    @PrimaryKey @JvmField var id: String = "",
    var userId: List<String>? = null,
    @JvmField @ColumnInfo(name = "_id") var _id: String? = null,
    var courseId: String? = null,
    @ColumnInfo(name = "_rev") var courseRev: String? = null,
    var languageOfInstruction: String? = null,
    var courseTitle: String? = null,
    var courseTitleNormal: String? = null,
    var memberLimit: Int? = null,
    var description: String? = null,
    var method: String? = null,
    var gradeLevel: String? = null,
    var subjectLevel: String? = null,
    var createdDate: Long = 0,
    var coverFileName: String? = null
) {
    @Ignore
    private var numberOfSteps: Int? = null
    @Ignore
    var courseSteps: MutableList<CourseStep>? = null
    @Ignore
    @Transient
    var isMyCourse: Boolean = false

    fun copy(userId: List<String>? = this.userId): MyCourse = MyCourse(
        id = id, userId = userId, _id = _id, courseId = courseId, courseRev = courseRev,
        languageOfInstruction = languageOfInstruction, courseTitle = courseTitle,
        courseTitleNormal = courseTitleNormal, memberLimit = memberLimit, description = description,
        method = method, gradeLevel = gradeLevel, subjectLevel = subjectLevel,
        createdDate = createdDate, coverFileName = coverFileName
    ).also {
        it.setNumberOfSteps(getNumberOfSteps())
        it.courseSteps = courseSteps
        it.isMyCourse = isMyCourse
    }

    fun setUserId(userId: String?) {
        if (userId.isNullOrBlank()) return
        val set = this.userId.orEmpty().filterTo(LinkedHashSet()) { it.isNotBlank() }
        set.add(userId)
        this.userId = set.toList()
    }

    fun removeUserId(userId: String?) {
        this.userId = this.userId.orEmpty().filter { it != userId }
    }

    fun getNumberOfSteps(): Int {
        return numberOfSteps ?: 0
    }

    fun setNumberOfSteps(numberOfSteps: Int?) {
        this.numberOfSteps = numberOfSteps
    }

    override fun toString(): String {
        return courseTitle ?: ""
    }

    companion object
}
