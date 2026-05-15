package org.ole.planet.myplanet.model

import android.text.TextUtils
import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.createStepResource
import org.ole.planet.myplanet.model.RealmStepExam.Companion.insertCourseStepsExams
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DownloadUtils.extractLinks
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.UrlUtils

open class RealmMyCourse : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var userId: RealmList<String>? = null
        private set
    @Index
    var courseId: String? = null
    var courseRev: String? = null
    var languageOfInstruction: String? = null
    var courseTitle: String? = null
    var memberLimit: Int? = null
    var description: String? = null
    var method: String? = null
    var gradeLevel: String? = null
    var subjectLevel: String? = null
    var createdDate: Long = 0
    private var numberOfSteps: Int? = null
    var courseSteps: RealmList<RealmCourseStep>? = null
    @Transient
    var isMyCourse: Boolean = false
    fun setUserId(userId: String?) {
        if (this.userId == null) {
            this.userId = RealmList()
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

        @JvmStatic
        fun addConcatenatedLink(link: String) {
            synchronized(concatenatedLinks) {
                concatenatedLinks.add(link)
            }
        }


        @JvmStatic
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






        @JvmStatic
        fun serialize(course: RealmMyCourse, realm: Realm): JsonObject {
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

            val stepsArray = JsonArray()
            val allResourcesForCourse = realm.where(RealmMyLibrary::class.java)
                .equalTo("courseId", course.courseId)
                .findAll()
            val resourcesByStepId = allResourcesForCourse.groupBy { it.stepId }

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
