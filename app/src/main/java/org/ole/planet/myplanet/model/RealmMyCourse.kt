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
        fun insertMyCourses(userId: String?, myCoursesDoc: JsonObject?, mRealm: Realm, spm: SharedPrefManager) {
            val id = JsonUtils.getString("_id", myCoursesDoc)
            var myMyCoursesDB = mRealm.where(RealmMyCourse::class.java).equalTo("id", id).findFirst()
            if (myMyCoursesDB == null) {
                myMyCoursesDB = mRealm.createObject(RealmMyCourse::class.java, id)
            }
            myMyCoursesDB?.setUserId(userId)
            myMyCoursesDB?.courseId = JsonUtils.getString("_id", myCoursesDoc)
            myMyCoursesDB?.courseRev = JsonUtils.getString("_rev", myCoursesDoc)
            myMyCoursesDB?.languageOfInstruction = JsonUtils.getString("languageOfInstruction", myCoursesDoc)
            myMyCoursesDB?.courseTitle = JsonUtils.getString("courseTitle", myCoursesDoc)
            myMyCoursesDB?.memberLimit = JsonUtils.getInt("memberLimit", myCoursesDoc)
            val description = JsonUtils.getString("description", myCoursesDoc)
            myMyCoursesDB?.description = description
            val links = extractLinks(description)
            val baseUrl = UrlUtils.getUrl()
            synchronized(concatenatedLinks) {
                for (link in links) {
                    concatenatedLinks.add("$baseUrl/$link")
                }
            }
            myMyCoursesDB?.method = JsonUtils.getString("method", myCoursesDoc)
            myMyCoursesDB?.gradeLevel = JsonUtils.getString("gradeLevel", myCoursesDoc)
            myMyCoursesDB?.subjectLevel = JsonUtils.getString("subjectLevel", myCoursesDoc)
            myMyCoursesDB?.createdDate = JsonUtils.getLong("createdDate", myCoursesDoc)
            val courseStepsJsonArray = JsonUtils.getJsonArray("steps", myCoursesDoc)
            val stepsSize = courseStepsJsonArray.size()
            myMyCoursesDB?.setNumberOfSteps(stepsSize)
            val courseStepsList = mutableListOf<RealmCourseStep>()

            for (i in 0 until stepsSize) {
                val stepElement = courseStepsJsonArray[i]
                val stepId = Base64.encodeToString(stepElement.toString().toByteArray(), Base64.NO_WRAP)
                val stepJson = stepElement.asJsonObject
                val step = RealmCourseStep()
                step.id = stepId
                step.stepTitle = JsonUtils.getString("stepTitle", stepJson)
                val stepDescription = JsonUtils.getString("description", stepJson)
                step.description = stepDescription
                val stepLinks = extractLinks(stepDescription)
                synchronized(concatenatedLinks) {
                    for (stepLink in stepLinks) {
                        concatenatedLinks.add("$baseUrl/$stepLink")
                    }
                }
                insertCourseStepsAttachments(myMyCoursesDB?.courseId, stepId, JsonUtils.getJsonArray("resources", stepJson), mRealm, spm)
                insertExam(stepJson, mRealm, stepId, i + 1, myMyCoursesDB?.courseId)
                insertSurvey(stepJson, mRealm, stepId, i + 1, myMyCoursesDB?.courseId, myMyCoursesDB?.createdDate)
                step.noOfResources = JsonUtils.getJsonArray("resources", stepJson).size()
                step.courseId = myMyCoursesDB?.courseId
                courseStepsList.add(step)
            }
            myMyCoursesDB?.courseSteps = RealmList()
            myMyCoursesDB?.courseSteps?.addAll(courseStepsList)
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


        private fun insertExam(stepContainer: JsonObject, mRealm: Realm, stepId: String, i: Int, myCoursesID: String?) {
            if (stepContainer.has("exam")) {
                val `object` = stepContainer.getAsJsonObject("exam")
                `object`.addProperty("stepNumber", i)
                insertCourseStepsExams(myCoursesID, stepId, `object`, mRealm)
            }
        }

        private fun insertSurvey(stepContainer: JsonObject, mRealm: Realm, stepId: String, i: Int, myCoursesID: String?, createdDate: Long?) {
            if (stepContainer.has("survey")) {
                val `object` = stepContainer.getAsJsonObject("survey")
                `object`.addProperty("stepNumber", i)
                `object`.addProperty("createdDate", createdDate)
                insertCourseStepsExams(myCoursesID, stepId, `object`, mRealm)
            }
        }

        private fun insertCourseStepsAttachments(myCoursesID: String?, stepId: String?, resources: JsonArray, mRealm: Realm?, spm: SharedPrefManager) {
            resources.forEach { resource ->
                if (mRealm != null) {
                    createStepResource(mRealm, resource.asJsonObject, myCoursesID, stepId, spm)
                }
            }
        }

        @JvmStatic
        fun insert(mRealm: Realm, myCoursesDoc: JsonObject?, spm: SharedPrefManager) {
            val startedTransaction = !mRealm.isInTransaction
            if (startedTransaction) {
                mRealm.beginTransaction()
            }
            try {
                insertMyCourses("", myCoursesDoc, mRealm, spm)
                if (startedTransaction) {
                    mRealm.commitTransaction()
                }
            } catch (e: Exception) {
                if (startedTransaction && mRealm.isInTransaction) {
                    mRealm.cancelTransaction()
                }
                throw e
            }
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