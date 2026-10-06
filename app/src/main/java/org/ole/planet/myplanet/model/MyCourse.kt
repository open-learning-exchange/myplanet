package org.ole.planet.myplanet.model

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.FileUtils.getOlePath
import org.ole.planet.myplanet.utils.KJson

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

    companion object {
        private val concatenatedLinks = HashSet<String>()

        fun getCoverImageFile(context: Context, courseId: String?, fileName: String?): java.io.File? {
            if (courseId.isNullOrBlank() || fileName.isNullOrBlank()) return null
            return java.io.File(
                "${getOlePath(context)}course_attachments/$courseId/$fileName"
            )
        }

        fun addConcatenatedLink(link: String) {
            synchronized(concatenatedLinks) {
                concatenatedLinks.add(link)
            }
        }

        fun saveConcatenatedLinksToPrefs(spm: SharedPrefManager) {
            val existingJsonLinks = spm.getConcatenatedLinks()
            val existingConcatenatedLinks = if (existingJsonLinks != null) {
                readStoredLinks(existingJsonLinks)
            } else {
                hashSetOf()
            }
            val linksToProcess: List<String>
            synchronized(concatenatedLinks) {
                linksToProcess = concatenatedLinks.toList()
            }
            existingConcatenatedLinks.addAll(linksToProcess)
            val jsonConcatenatedLinks = KJson.gsonToJson(JsonArray(existingConcatenatedLinks.map { JsonPrimitive(it) }))
            spm.setConcatenatedLinks(jsonConcatenatedLinks)
        }

        /**
         * Gson's `fromJson(text, Array<String>::class.java)` as a set: string, number and boolean
         * entries read as their text, `null` stays `null`; blank text (Gson's `null` array) and
         * any other shape throw, as they did.
         */
        private fun readStoredLinks(text: String): HashSet<String?> {
            val array = KJson.parseArrayOrNull(text) ?: throw NullPointerException("stored links are blank")
            return array.mapTo(HashSet()) { element ->
                when (element) {
                    is JsonNull -> null
                    is JsonPrimitive -> element.content
                    else -> throw IllegalStateException("Expected a string but was $element")
                }
            }
        }

        fun serialize(course: MyCourse, resourcesByStepId: Map<String?, List<MyLibrary>>): JsonObject = buildJsonObject {
            put("_id", course.courseId)
            put("_rev", course.courseRev)
            put("courseTitle", course.courseTitle)
            put("description", course.description)
            put("languageOfInstruction", course.languageOfInstruction)
            put("gradeLevel", course.gradeLevel)
            put("subjectLevel", course.subjectLevel)
            put("createdDate", course.createdDate)
            put("method", course.method)
            put("memberLimit", course.memberLimit)
            course.coverFileName?.let { put("coverFileName", it) }

            put("steps", buildJsonArray {
                course.courseSteps?.forEach { step ->
                    add(buildJsonObject {
                        put("stepTitle", step.stepTitle)
                        put("description", step.description)
                        put("id", step.id)

                        val stepResources = resourcesByStepId[step.id] ?: emptyList()
                        put("resources", buildJsonArray {
                            stepResources.forEach { resource ->
                                add(resource.serializeResource())
                            }
                        })
                    })
                }
            })
            put("images", buildJsonArray { })
        }
    }
}
