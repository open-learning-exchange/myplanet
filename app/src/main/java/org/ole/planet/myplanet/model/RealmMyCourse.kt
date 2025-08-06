package org.ole.planet.myplanet.model

import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.util.Log
import android.text.TextUtils
import android.util.Base64
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.annotations.PrimaryKey
import io.realm.kotlin.where
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.createStepResource
import org.ole.planet.myplanet.model.RealmStepExam.Companion.insertCourseStepsExams
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.DownloadUtils.extractLinks
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.Utilities

open class RealmMyCourse : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var userId: RealmList<String>? = null
        private set
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
        if (!this.userId?.contains(userId)!! && !TextUtils.isEmpty(userId)) {
            this.userId?.add(userId)
            
            // Track local course joining action
            trackLocalCourseChange(userId, this.courseId, "joined")
        }
    }

    fun setUserIdDuringSync(userId: String?) {
        if (this.userId == null) {
            this.userId = RealmList()
        }
        if (!this.userId?.contains(userId)!! && !TextUtils.isEmpty(userId)) {
            this.userId?.add(userId)
            // Don't track this action as it's from server sync, not a local user action
        }
    }

    fun removeUserId(userId: String?) {
        val wasEnrolled = this.userId?.contains(userId) == true
        if (wasEnrolled) {
            this.userId?.remove(userId)
            
            // Track local course leaving action
            trackLocalCourseChange(userId, this.courseId, "left")
        }
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
        private val gson = Gson()
        private val concatenatedLinks = ArrayList<String>()

        @JvmStatic
        fun insertMyCourses(userId: String?, myCoursesDoc: JsonObject?, mRealm: Realm) {
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val id = JsonUtils.getString("_id", myCoursesDoc)
            var myMyCoursesDB = mRealm.where(RealmMyCourse::class.java).equalTo("id", id).findFirst()
            val isNewCourse = myMyCoursesDB == null
            
            if (isNewCourse) {
                myMyCoursesDB = mRealm.createObject(RealmMyCourse::class.java, id)
            }
            
            // Only enroll user if they haven't locally left the course
            if (!isUserLocallyLeftCourse(userId, id)) {
                myMyCoursesDB?.setUserIdDuringSync(userId)
            }
            myMyCoursesDB?.courseId = JsonUtils.getString("_id", myCoursesDoc)
            myMyCoursesDB?.courseRev = JsonUtils.getString("_rev", myCoursesDoc)
            myMyCoursesDB?.languageOfInstruction = JsonUtils.getString("languageOfInstruction", myCoursesDoc)
            myMyCoursesDB?.courseTitle = JsonUtils.getString("courseTitle", myCoursesDoc)
            myMyCoursesDB?.memberLimit = JsonUtils.getInt("memberLimit", myCoursesDoc)
            myMyCoursesDB?.description = JsonUtils.getString("description", myCoursesDoc)
            val description = JsonUtils.getString("description", myCoursesDoc)
            val links = extractLinks(description)
            val baseUrl = Utilities.getUrl()
            for (link in links) {
                val concatenatedLink = "$baseUrl/$link"
                concatenatedLinks.add(concatenatedLink)
            }
            myMyCoursesDB?.method = JsonUtils.getString("method", myCoursesDoc)
            myMyCoursesDB?.gradeLevel = JsonUtils.getString("gradeLevel", myCoursesDoc)
            myMyCoursesDB?.subjectLevel = JsonUtils.getString("subjectLevel", myCoursesDoc)
            myMyCoursesDB?.createdDate = JsonUtils.getLong("createdDate", myCoursesDoc)
            myMyCoursesDB?.setNumberOfSteps(JsonUtils.getJsonArray("steps", myCoursesDoc).size())
            val courseStepsJsonArray = JsonUtils.getJsonArray("steps", myCoursesDoc)
            val courseStepsList = mutableListOf<RealmCourseStep>()

            for (i in 0 until courseStepsJsonArray.size()) {
                val stepId = Base64.encodeToString(courseStepsJsonArray[i].toString().toByteArray(), Base64.NO_WRAP)
                val stepJson = courseStepsJsonArray[i].asJsonObject
                val step = RealmCourseStep()
                step.id = stepId
                step.stepTitle = JsonUtils.getString("stepTitle", stepJson)
                step.description = JsonUtils.getString("description", stepJson)
                val stepDescription = JsonUtils.getString("description", stepJson)
                val stepLinks = extractLinks(stepDescription)
                for (stepLink in stepLinks) {
                    val concatenatedLink = "$baseUrl/$stepLink"
                    concatenatedLinks.add(concatenatedLink)
                }
                insertCourseStepsAttachments(myMyCoursesDB?.courseId, stepId, JsonUtils.getJsonArray("resources", stepJson), mRealm)
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
        fun saveConcatenatedLinksToPrefs() {
            val settings: SharedPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val existingJsonLinks = settings.getString("concatenated_links", null)
            val existingConcatenatedLinks = if (existingJsonLinks != null) {
                gson.fromJson(existingJsonLinks, Array<String>::class.java).toMutableList()
            } else {
                mutableListOf()
            }
            val linksToProcess: List<String>
            synchronized(concatenatedLinks) {
                linksToProcess = concatenatedLinks.toList()
            }
            for (link in linksToProcess) {
                if (!existingConcatenatedLinks.contains(link)) {
                    existingConcatenatedLinks.add(link)
                }
            }
            val jsonConcatenatedLinks = gson.toJson(existingConcatenatedLinks)
            settings.edit { putString("concatenated_links", jsonConcatenatedLinks) }
        }

        fun getCourseSteps(mRealm: Realm, courseId: String?): List<RealmCourseStep> {
            val myCourse = mRealm.where<RealmMyCourse>().equalTo("id", courseId).findFirst()
            val courseSteps = myCourse?.courseSteps ?: emptyList()
            return courseSteps
        }

        fun getCourseStepIds(mRealm: Realm, courseId: String?): Array<String?> {
            val course = mRealm.where<RealmMyCourse>().equalTo("courseId", courseId).findFirst()
            val stepIds = course?.courseSteps?.map { it.id }?.toTypedArray() ?: emptyArray()
            return stepIds
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

        private fun insertCourseStepsAttachments(myCoursesID: String?, stepId: String?, resources: JsonArray, mRealm: Realm?) {
            resources.forEach { resource ->
                if (mRealm != null) {
                    createStepResource(mRealm, resource.asJsonObject, myCoursesID, stepId)
                }
            }
        }

        @JvmStatic
        fun getMyByUserId(mRealm: Realm, settings: SharedPreferences?): RealmResults<RealmMyCourse> {
            val userId = settings?.getString("userId", "--")
            return mRealm.where(RealmMyCourse::class.java)
                .equalTo("userId", userId)
                .findAll()
        }

        @JvmStatic
        fun getMyCourseByUserId(userId: String?, libs: List<RealmMyCourse>?): List<RealmMyCourse> {
            val libraries: MutableList<RealmMyCourse> = ArrayList()
            for (item in libs ?: emptyList()) {
                if (item.userId?.contains(userId) == true) {
                    libraries.add(item)
                }
            }
            return libraries
        }

        @JvmStatic
        fun getAllCourses(userId: String?, libs: List<RealmMyCourse>): List<RealmMyCourse> {
            val libraries: MutableList<RealmMyCourse> = ArrayList()
            for (item in libs) {
                item.isMyCourse = item.userId?.contains(userId)!!
                libraries.add(item)
            }
            return libraries
        }

        @JvmStatic
        fun getOurCourse(userId: String?, libs: List<RealmMyCourse>): List<RealmMyCourse> {
            val libraries: MutableList<RealmMyCourse> = ArrayList()
            for (item in libs) {
                if (!item.userId?.contains(userId)!!) {
                    libraries.add(item)
                }
            }
            return libraries
        }

        @JvmStatic
        fun isMyCourse(userId: String?, courseId: String?, realm: Realm): Boolean {
            return getMyCourseByUserId(userId, realm.where(RealmMyCourse::class.java).equalTo("courseId", courseId).findAll()).isNotEmpty()
        }

        @JvmStatic
        fun getCourseByCourseId(courseId: String, mRealm: Realm): RealmMyCourse? {
            return mRealm.where(RealmMyCourse::class.java).equalTo("courseId", courseId).findFirst()
        }

        @JvmStatic
        fun insert(mRealm: Realm, myCoursesDoc: JsonObject?) {
            if (!mRealm.isInTransaction) {
                mRealm.beginTransaction()
            }
            insertMyCourses("", myCoursesDoc, mRealm)
            mRealm.commitTransaction()
        }

        @JvmStatic
        fun getMyCourse(mRealm: Realm, id: String?): RealmMyCourse? {
            return mRealm.where(RealmMyCourse::class.java).equalTo("courseId", id).findFirst()
        }

        @JvmStatic
        fun createMyCourse(course: RealmMyCourse?, mRealm: Realm, id: String?) {
            if (!mRealm.isInTransaction) {
                mRealm.beginTransaction()
            }
            course?.setUserId(id)
            mRealm.commitTransaction()
        }

        @JvmStatic
        fun getMyCourseIds(realm: Realm?, userId: String?): JsonArray {
            val myCourses = getMyCourseByUserId(userId, realm?.where(RealmMyCourse::class.java)?.findAll())
            val ids = JsonArray()
            for (lib in myCourses) {
                ids.add(lib.courseId)
            }
            return ids
        }

        @JvmStatic
        fun trackLocalCourseChange(userId: String?, courseId: String?, action: String) {
            if (userId.isNullOrEmpty() || courseId.isNullOrEmpty()) return
            
            val settings: SharedPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val existingChanges = settings.getString("local_course_changes", "[]")
            val changesArray = gson.fromJson(existingChanges, JsonArray::class.java)
            
            val change = JsonObject().apply {
                addProperty("userId", userId)
                addProperty("courseId", courseId)
                addProperty("action", action)
                addProperty("timestamp", System.currentTimeMillis())
            }
            
            changesArray.add(change)
            Log.d("RealmMyCourse", "Tracked local course change: $action for user $userId in course $courseId")
            
            settings.edit {
                putString("local_course_changes", gson.toJson(changesArray))
            }
        }

        @JvmStatic
        fun getLocalCourseChanges(): JsonArray {
            val settings: SharedPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val existingChanges = settings.getString("local_course_changes", "[]")
            return gson.fromJson(existingChanges, JsonArray::class.java)
        }

        @JvmStatic
        fun isUserLocallyLeftCourse(userId: String?, courseId: String?): Boolean {
            if (userId.isNullOrEmpty() || courseId.isNullOrEmpty()) return false
            
            val changes = getLocalCourseChanges()
            for (i in 0 until changes.size()) {
                val change = changes[i].asJsonObject
                if (change.get("userId").asString == userId && 
                    change.get("courseId").asString == courseId && 
                    change.get("action").asString == "left") {
                    return true
                }
            }
            return false
        }
    }
}
