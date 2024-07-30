package org.ole.planet.myplanet.model

import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.text.TextUtils
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.opencsv.CSVWriter
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.annotations.PrimaryKey
import io.realm.kotlin.where
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.model.RealmFeedback.Companion.feedbacksDataList
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.createStepResource
import org.ole.planet.myplanet.model.RealmStepExam.Companion.insertCourseStepsExams
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.Utilities
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.regex.Pattern

open class RealmMyCourse : RealmObject() {
    @JvmField
    @PrimaryKey
    var id: String? = null
    var userId: RealmList<String>? = null
        private set
    @JvmField
    var courseId: String? = null
    @JvmField
    var course_rev: String? = null
    @JvmField
    var languageOfInstruction: String? = null
    @JvmField
    var courseTitle: String? = null
    @JvmField
    var memberLimit: Int? = null
    @JvmField
    var description: String? = null
    @JvmField
    var method: String? = null
    @JvmField
    var gradeLevel: String? = null
    @JvmField
    var subjectLevel: String? = null
    @JvmField
    var createdDate: Long = 0
    private var numberOfSteps: Int? = null
    var courseSteps: RealmList<RealmCourseStep>? = null
    fun setUserId(userId: String?) {
        if (this.userId == null) {
            this.userId = RealmList()
        }
        if (!this.userId?.contains(userId)!! && !TextUtils.isEmpty(userId)) {
            this.userId?.add(userId)
        }
    }

    fun removeUserId(userId: String?) {
        this.userId?.remove(userId)
    }

    fun getnumberOfSteps(): Int {
        return numberOfSteps ?: 0
    }

    fun setnumberOfSteps(numberOfSteps: Int?) {
        this.numberOfSteps = numberOfSteps
    }

    override fun toString(): String {
        return courseTitle ?: ""
    }

    companion object {
        private val gson = Gson()
        private val concatenatedLinks = ArrayList<String>()
        val courseDataList: MutableList<Array<String>> = mutableListOf()

        @JvmStatic
        fun insertMyCourses(userId: String?, myCousesDoc: JsonObject?, mRealm: Realm) {
            val settings: SharedPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            if (!mRealm.isInTransaction) {
                mRealm.beginTransaction()
            }
            val id = JsonUtils.getString("_id", myCousesDoc)
            var myMyCoursesDB = mRealm.where(RealmMyCourse::class.java).equalTo("id", id).findFirst()
            if (myMyCoursesDB == null) {
                myMyCoursesDB = mRealm.createObject(RealmMyCourse::class.java, id)
            }
            myMyCoursesDB?.setUserId(userId)
            myMyCoursesDB?.courseId = JsonUtils.getString("_id", myCousesDoc)
            myMyCoursesDB?.course_rev = JsonUtils.getString("_rev", myCousesDoc)
            myMyCoursesDB?.languageOfInstruction = JsonUtils.getString("languageOfInstruction", myCousesDoc)
            myMyCoursesDB?.courseTitle = JsonUtils.getString("courseTitle", myCousesDoc)
            myMyCoursesDB?.memberLimit = JsonUtils.getInt("memberLimit", myCousesDoc)
            myMyCoursesDB?.description = JsonUtils.getString("description", myCousesDoc)
            val description = JsonUtils.getString("description", myCousesDoc)
            val links = extractLinks(description)
            val baseUrl = Utilities.getUrl()
            for (link in links) {
                val concatenatedLink = "$baseUrl/$link"
                concatenatedLinks.add(concatenatedLink)
            }
            myMyCoursesDB?.method = JsonUtils.getString("method", myCousesDoc)
            myMyCoursesDB?.gradeLevel = JsonUtils.getString("gradeLevel", myCousesDoc)
            myMyCoursesDB?.subjectLevel = JsonUtils.getString("subjectLevel", myCousesDoc)
            myMyCoursesDB?.createdDate = JsonUtils.getLong("createdDate", myCousesDoc)
            myMyCoursesDB?.setnumberOfSteps(JsonUtils.getJsonArray("steps", myCousesDoc).size())
            val courseStepsJsonArray = JsonUtils.getJsonArray("steps", myCousesDoc)
            val courseStepsList = mutableListOf<RealmCourseStep>()

            for (i in 0 until courseStepsJsonArray.size()) {
                val step_id = Base64.encodeToString(courseStepsJsonArray[i].toString().toByteArray(), Base64.NO_WRAP)
                val stepJson = courseStepsJsonArray[i].asJsonObject
                val step = RealmCourseStep()
                step.id = step_id
                step.stepTitle = JsonUtils.getString("stepTitle", stepJson)
                step.description = JsonUtils.getString("description", stepJson)
                val stepDescription = JsonUtils.getString("description", stepJson)
                val stepLinks = extractLinks(stepDescription)
                for (stepLink in stepLinks) {
                    val concatenatedLink = "$baseUrl/$stepLink"
                    concatenatedLinks.add(concatenatedLink)
                }
                insertCourseStepsAttachments(myMyCoursesDB?.courseId, step_id, JsonUtils.getJsonArray("resources", stepJson), mRealm)
                insertExam(stepJson, mRealm, step_id, i + 1, myMyCoursesDB?.courseId)
                step.noOfResources = JsonUtils.getJsonArray("resources", stepJson).size()
                step.courseId = myMyCoursesDB?.courseId
                courseStepsList.add(step)
            }

            if (mRealm.isInTransaction) {
                mRealm.commitTransaction()
            }

            if (!mRealm.isInTransaction) {
                mRealm.beginTransaction()
            }
            myMyCoursesDB?.courseSteps = RealmList()
            myMyCoursesDB?.courseSteps?.addAll(courseStepsList)
            mRealm.commitTransaction()

            val csvRow = arrayOf(
                JsonUtils.getString("_id", myCousesDoc),
                JsonUtils.getString("_rev", myCousesDoc),
                JsonUtils.getString("languageOfInstruction", myCousesDoc),
                JsonUtils.getString("courseTitle", myCousesDoc),
                JsonUtils.getInt("memberLimit", myCousesDoc).toString(),
                JsonUtils.getString("description", myCousesDoc),
                JsonUtils.getString("method", myCousesDoc),
                JsonUtils.getString("gradeLevel", myCousesDoc),
                JsonUtils.getString("subjectLevel", myCousesDoc),
                JsonUtils.getLong("createdDate", myCousesDoc).toString(),
                JsonUtils.getJsonArray("steps", myCousesDoc).toString()
            )
            courseDataList.add(csvRow)
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                val writer = CSVWriter(FileWriter(file))
                writer.writeNext(arrayOf("courseId", "course_rev", "languageOfInstruction", "courseTitle", "memberLimit", "description", "method", "gradeLevel", "subjectLevel", "createdDate", "steps"))
                for (row in data) {
                    writer.writeNext(row)
                }
                writer.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun courseWriteCsv() {
            writeCsv("${context.getExternalFilesDir(null)}/ole/course.csv", courseDataList)
        }

        private fun extractLinks(text: String?): ArrayList<String> {
            val links = ArrayList<String>()
            val pattern = Pattern.compile("!\\[.*?]\\((.*?)\\)")
            val matcher = text?.let { pattern.matcher(it) }
            if (matcher != null) {
                while (matcher.find()) {
                    val link = matcher.group(1)
                    if (link != null) {
                        if (link.isNotEmpty()) {
                            links.add(link)
                        }
                    }
                }
            }
            return links
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

            synchronized(concatenatedLinks) {
                for (link in concatenatedLinks) {
                    if (!existingConcatenatedLinks.contains(link)) {
                        existingConcatenatedLinks.add(link)
                    }
                }
            }

            val jsonConcatenatedLinks = gson.toJson(existingConcatenatedLinks)
            settings.edit().putString("concatenated_links", jsonConcatenatedLinks).apply()
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

        private fun insertExam(stepContainer: JsonObject, mRealm: Realm, step_id: String, i: Int, myCoursesID: String?) {
            if (stepContainer.has("exam")) {
                val `object` = stepContainer.getAsJsonObject("exam")
                `object`.addProperty("stepNumber", i)
                insertCourseStepsExams(myCoursesID, step_id, `object`, mRealm)
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
        fun insert(mRealm: Realm, myCousesDoc: JsonObject?) {
            insertMyCourses("", myCousesDoc, mRealm)
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
    }
}