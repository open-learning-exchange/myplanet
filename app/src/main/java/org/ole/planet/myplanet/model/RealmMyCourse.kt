package org.ole.planet.myplanet.model

import android.content.Context
import android.text.TextUtils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.FileUtils.getOlePath
import org.ole.planet.myplanet.utils.JsonUtils

open class RealmMyCourse {
    var id: String? = null
    var userId: MutableList<String>? = null
        private set
    var courseId: String? = null
    var courseRev: String? = null
    var languageOfInstruction: String? = null
    var courseTitle: String? = null
    var courseTitleNormal: String? = null
    var memberLimit: Int? = null
    var description: String? = null
    var method: String? = null
    var gradeLevel: String? = null
    var subjectLevel: String? = null
    var createdDate: Long = 0
    var coverFileName: String? = null
    private var numberOfSteps: Int? = null
    var courseSteps: MutableList<RealmCourseStep>? = null
    @Transient
    var isMyCourse: Boolean = false
    fun setUserId(userId: String?) {
        if (this.userId == null) {
            this.userId = mutableListOf()
        }
        if (this.userId?.contains(userId) != true && !TextUtils.isEmpty(userId)) {
            this.userId?.add(userId)
        }
    }

    fun removeUserId(userId: String?) {
        this.userId?.remove(userId)
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
                JsonUtils.gson.fromJson(existingJsonLinks, Array<String>::class.java).toMutableSet()
            } else {
                mutableSetOf()
            }
            val linksToProcess: List<String>
            synchronized(concatenatedLinks) {
                linksToProcess = concatenatedLinks.toList()
            }
            val existingSet = existingConcatenatedLinks.toHashSet()
            for (link in linksToProcess) {
                existingSet.add(link)
            }
            val jsonConcatenatedLinks = JsonUtils.gson.toJson(existingSet.toList())
            spm.setConcatenatedLinks(jsonConcatenatedLinks)
        }

        fun serialize(course: RealmMyCourse, resourcesByStepId: Map<String?, List<RealmMyLibrary>>): JsonObject {
            val obj = JsonObject()
            obj.addProperty("_id", course.courseId)
            obj.addProperty("_rev", course.courseRev)
            obj.addProperty("courseTitle", course.courseTitle)
            obj.addProperty("description", course.description)
            obj.addProperty("languageOfInstruction", course.languageOfInstruction)
            obj.addProperty("gradeLevel", course.gradeLevel)
            obj.addProperty("subjectLevel", course.subjectLevel)
            obj.addProperty("createdDate", course.createdDate)
            obj.addProperty("method", course.method)
            obj.addProperty("memberLimit", course.memberLimit)
            course.coverFileName?.let { obj.addProperty("coverFileName", it) }

            val stepsArray = JsonArray()

            course.courseSteps?.forEach { step ->
                val stepObj = JsonObject()
                stepObj.addProperty("stepTitle", step.stepTitle)
                stepObj.addProperty("description", step.description)
                stepObj.addProperty("id", step.id)

                val resourcesArray = JsonArray()
                val stepResources = resourcesByStepId[step.id] ?: emptyList()

                stepResources.forEach { resource ->
                    resourcesArray.add(resource.serializeResource())
                }
                stepObj.add("resources", resourcesArray)
                stepsArray.add(stepObj)
            }
            obj.add("steps", stepsArray)
            obj.add("images", JsonArray())
            return obj
        }
    }
}
