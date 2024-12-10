package org.ole.planet.myplanet.model

import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.text.TextUtils
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.opencsv.CSVWriter
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.createStepResource
import org.ole.planet.myplanet.model.RealmStepExam.Companion.insertCourseStepsExams
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.JsonUtils
import java.io.File
import java.io.FileWriter
import java.io.IOException

class RealmMyCourse : RealmObject {
    @PrimaryKey
    var id: String = ""
    var userId: RealmList<String> = realmListOf()
    var courseId: String = ""
    var courseRev: String = ""
    var languageOfInstruction: String = ""
    var courseTitle: String = ""
    var memberLimit: Int = 0
    var description: String = ""
    var method: String = ""
    var gradeLevel: String = ""
    var subjectLevel: String = ""
    var createdDate: Long = 0
    private var numberOfSteps: Int = 0
    var courseSteps: RealmList<RealmCourseStep> = realmListOf()

    @Transient
    var isMyCourse: Boolean = false

    fun addUserId(userId: String) {
        if (!TextUtils.isEmpty(userId) && !this.userId.contains(userId)) {
            this.userId.add(userId)
        }
    }

    fun removeUserId(userId: String) {
        this.userId.remove(userId)
    }

    fun getNumberOfSteps(): Int = numberOfSteps

    fun setNumberOfSteps(steps: Int) {
        numberOfSteps = steps
    }

    override fun toString(): String = courseTitle

    companion object {
        private val gson = Gson()
        private val concatenatedLinks = ArrayList<String>()
        val courseDataList: MutableList<Array<String>> = mutableListOf()

        fun insertMyCourses(userId: String, myCoursesDoc: JsonObject, realm: Realm) {
            CoroutineScope(Dispatchers.IO).launch {
                val ID = JsonUtils.getString("_id", myCoursesDoc)
                realm.writeBlocking {
                    var myMyCourseDB = this.query<RealmMyCourse>("id == $0", ID).first().find()

                    if (myMyCourseDB == null) {
                        myMyCourseDB = RealmMyCourse().apply { this.id = ID }
                        copyToRealm(myMyCourseDB)
                    }

                    myMyCourseDB.apply {
                        addUserId(userId)
                        courseId = JsonUtils.getString("_id", myCoursesDoc)
                        courseRev = JsonUtils.getString("_rev", myCoursesDoc)
                        languageOfInstruction = JsonUtils.getString("languageOfInstruction", myCoursesDoc)
                        courseTitle = JsonUtils.getString("courseTitle", myCoursesDoc)
                        memberLimit = JsonUtils.getInt("memberLimit", myCoursesDoc)
                        description = JsonUtils.getString("description", myCoursesDoc)
                        method = JsonUtils.getString("method", myCoursesDoc)
                        gradeLevel = JsonUtils.getString("gradeLevel", myCoursesDoc)
                        subjectLevel = JsonUtils.getString("subjectLevel", myCoursesDoc)
                        createdDate = JsonUtils.getLong("createdDate", myCoursesDoc)
                        setNumberOfSteps(JsonUtils.getJsonArray("steps", myCoursesDoc).size())

                        courseSteps.clear()
                    }
                }

                val courseStepsJsonArray = JsonUtils.getJsonArray("steps", myCoursesDoc)

                for (i in 0 until courseStepsJsonArray.size()) {
                    val stepJson = courseStepsJsonArray[i].asJsonObject
                    val stepId = Base64.encodeToString(stepJson.toString().toByteArray(), Base64.NO_WRAP)

                    realm.writeBlocking {
                        val step = RealmCourseStep().apply {
                            id = stepId
                            stepTitle = JsonUtils.getString("stepTitle", stepJson)
                            description = JsonUtils.getString("description", stepJson)
                            courseId = ID
                            noOfResources = JsonUtils.getJsonArray("resources", stepJson).size()
                        }
                        copyToRealm(step)
                    }

                    // Safely call suspend functions
                    insertExam(stepJson, realm, stepId, i + 1, ID)
                    insertSurvey(stepJson, realm, stepId, i + 1, ID, JsonUtils.getLong("createdDate", myCoursesDoc))
                }
            }

            val csvRow = arrayOf(
                JsonUtils.getString("_id", myCoursesDoc),
                JsonUtils.getString("_rev", myCoursesDoc),
                JsonUtils.getString("languageOfInstruction", myCoursesDoc),
                JsonUtils.getString("courseTitle", myCoursesDoc),
                JsonUtils.getInt("memberLimit", myCoursesDoc).toString(),
                JsonUtils.getString("description", myCoursesDoc),
                JsonUtils.getString("method", myCoursesDoc),
                JsonUtils.getString("gradeLevel", myCoursesDoc),
                JsonUtils.getString("subjectLevel", myCoursesDoc),
                JsonUtils.getLong("createdDate", myCoursesDoc).toString(),
                JsonUtils.getJsonArray("steps", myCoursesDoc).toString()
            )
            courseDataList.add(csvRow)
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                CSVWriter(FileWriter(file)).use { writer ->
                    writer.writeNext(arrayOf(
                        "courseId", "course_rev", "languageOfInstruction",
                        "courseTitle", "memberLimit", "description", "method",
                        "gradeLevel", "subjectLevel", "createdDate", "steps"
                    ))
                    data.forEach { writer.writeNext(it) }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun courseWriteCsv() {
            writeCsv("${context.getExternalFilesDir(null)}/ole/course.csv", courseDataList)
        }

        fun saveConcatenatedLinksToPrefs() {
            val settings: SharedPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val existingJsonLinks = settings.getString("concatenated_links", null)
            val existingConcatenatedLinks = existingJsonLinks?.let {
                gson.fromJson(it, Array<String>::class.java).toMutableList()
            } ?: mutableListOf()

            synchronized(concatenatedLinks) {
                concatenatedLinks.forEach { link ->
                    if (!existingConcatenatedLinks.contains(link)) {
                        existingConcatenatedLinks.add(link)
                    }
                }
            }

            settings.edit().putString("concatenated_links", gson.toJson(existingConcatenatedLinks)).apply()
        }

        fun getCourseSteps(realm: Realm, courseId: String): List<RealmCourseStep> {
            return realm.query<RealmMyCourse>("id == $0", courseId).first().find()?.courseSteps?.toList()
                ?: emptyList()
        }

        fun getCourseStepIds(realm: Realm, courseId: String): Array<String?> {
            return realm.query<RealmMyCourse>("courseId == $0", courseId).first().find()?.courseSteps?.map { it.id }?.toTypedArray() ?: emptyArray()
        }

        private suspend fun insertExam(stepContainer: JsonObject, realm: Realm, stepId: String, stepNumber: Int, courseId: String) {
            if (stepContainer.has("exam")) {
                val examObject = stepContainer.getAsJsonObject("exam").apply {
                    addProperty("stepNumber", stepNumber)
                }
                insertCourseStepsExams(courseId, stepId, examObject, realm)
            }
        }

        private suspend fun insertSurvey(stepContainer: JsonObject, realm: Realm, stepId: String, stepNumber: Int, courseId: String, createdDate: Long) {
            if (stepContainer.has("survey")) {
                val surveyObject = stepContainer.getAsJsonObject("survey").apply {
                    addProperty("stepNumber", stepNumber)
                    addProperty("createdDate", createdDate)
                }
                insertCourseStepsExams(courseId, stepId, surveyObject, realm)
            }
        }

        private fun insertCourseStepsAttachments(courseId: String, stepId: String, resources: JsonArray, realm: Realm) {
            resources.forEach { resource ->
                createStepResource(realm, resource.asJsonObject, courseId, stepId)
            }
        }

        fun getMyByUserId(realm: Realm, settings: SharedPreferences?): List<RealmMyCourse> {
            val userId = settings?.getString("userId", "--") ?: return emptyList()
            return realm.query<RealmMyCourse>("userId == $0", userId).find()
        }

        fun getMyCourseByUserId(userId: String, courses: List<RealmMyCourse>): List<RealmMyCourse> {
            return courses.filter { course -> course.userId.contains(userId) }
        }

        fun getOurCourse(userId: String, courses: List<RealmMyCourse>): List<RealmMyCourse> {
            return courses.filter { course -> !course.userId.contains(userId) }
        }

        fun isMyCourse(userId: String, courseId: String, realm: Realm): Boolean {
            val courses = realm.query<RealmMyCourse>("courseId == $0", courseId).find()
            return getMyCourseByUserId(userId, courses).isNotEmpty()
        }

        fun getCourseByCourseId(courseId: String, realm: Realm): RealmMyCourse? {
            return realm.query<RealmMyCourse>("courseId == $0", courseId).first().find()
        }

        fun insert(realm: Realm, myCoursesDoc: JsonObject) {
            insertMyCourses("", myCoursesDoc, realm)
        }

        fun getMyCourse(realm: Realm, id: String): RealmMyCourse? {
            return realm.query<RealmMyCourse>("courseId == $0", id).first().find()
        }

        fun createMyCourse(course: RealmMyCourse?, realm: Realm, id: String) {
            realm.writeBlocking {
                course?.addUserId(id)
            }
        }

        fun getMyCourseIds(realm: Realm?, userId: String): JsonArray {
            val myCourses = realm?.let {
                getMyCourseByUserId(userId, it.query<RealmMyCourse>().find())
            } ?: emptyList()

            return JsonArray().apply {
                myCourses.forEach { course ->
                    add(course.courseId)
                }
            }
        }
    }
}


//package org.ole.planet.myplanet.model
//
//import android.content.Context.MODE_PRIVATE
//import android.content.SharedPreferences
//import android.text.TextUtils
//import android.util.Base64
//import com.google.gson.Gson
//import com.google.gson.JsonArray
//import com.google.gson.JsonObject
//import com.opencsv.CSVWriter
//import io.realm.Realm
//import io.realm.RealmList
//import io.realm.RealmObject
//import io.realm.RealmResults
//import io.realm.annotations.PrimaryKey
//import io.realm.kotlin.where
//import org.ole.planet.myplanet.MainApplication.Companion.context
//import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.createStepResource
//import org.ole.planet.myplanet.model.RealmStepExam.Companion.insertCourseStepsExams
//import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
//import org.ole.planet.myplanet.utilities.DownloadUtils.extractLinks
//import org.ole.planet.myplanet.utilities.JsonUtils
//import org.ole.planet.myplanet.utilities.Utilities
//import java.io.File
//import java.io.FileWriter
//import java.io.IOException
//
//open class RealmMyCourse : RealmObject() {
//    @PrimaryKey
//    var id: String? = null
//    var userId: RealmList<String>? = null
//        private set
//    var courseId: String? = null
//    var courseRev: String? = null
//    var languageOfInstruction: String? = null
//    var courseTitle: String? = null
//    var memberLimit: Int? = null
//    var description: String? = null
//    var method: String? = null
//    var gradeLevel: String? = null
//    var subjectLevel: String? = null
//    var createdDate: Long = 0
//    private var numberOfSteps: Int? = null
//    var courseSteps: RealmList<RealmCourseStep>? = null
//    @Transient
//    var isMyCourse: Boolean = false
//    fun setUserId(userId: String?) {
//        if (this.userId == null) {
//            this.userId = RealmList()
//        }
//        if (!this.userId?.contains(userId)!! && !TextUtils.isEmpty(userId)) {
//            this.userId?.add(userId)
//        }
//    }
//
//    fun removeUserId(userId: String?) {
//        this.userId?.remove(userId)
//    }
//
//    fun getNumberOfSteps(): Int {
//        return numberOfSteps ?: 0
//    }
//
//    fun setNumberOfSteps(numberOfSteps: Int?) {
//        this.numberOfSteps = numberOfSteps
//    }
//
//    override fun toString(): String {
//        return courseTitle ?: ""
//    }
//
//    companion object {
//        private val gson = Gson()
//        private val concatenatedLinks = ArrayList<String>()
//        val courseDataList: MutableList<Array<String>> = mutableListOf()
//
//        @JvmStatic
//        fun insertMyCourses(userId: String?, myCoursesDoc: JsonObject?, mRealm: Realm) {
//            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
//            if (!mRealm.isInTransaction) {
//                mRealm.beginTransaction()
//            }
//            val id = JsonUtils.getString("_id", myCoursesDoc)
//            var myMyCoursesDB = mRealm.where(RealmMyCourse::class.java).equalTo("id", id).findFirst()
//            if (myMyCoursesDB == null) {
//                myMyCoursesDB = mRealm.createObject(RealmMyCourse::class.java, id)
//            }
//            myMyCoursesDB?.setUserId(userId)
//            myMyCoursesDB?.courseId = JsonUtils.getString("_id", myCoursesDoc)
//            myMyCoursesDB?.courseRev = JsonUtils.getString("_rev", myCoursesDoc)
//            myMyCoursesDB?.languageOfInstruction = JsonUtils.getString("languageOfInstruction", myCoursesDoc)
//            myMyCoursesDB?.courseTitle = JsonUtils.getString("courseTitle", myCoursesDoc)
//            myMyCoursesDB?.memberLimit = JsonUtils.getInt("memberLimit", myCoursesDoc)
//            myMyCoursesDB?.description = JsonUtils.getString("description", myCoursesDoc)
//            val description = JsonUtils.getString("description", myCoursesDoc)
//            val links = extractLinks(description)
//            val baseUrl = Utilities.getUrl()
//            for (link in links) {
//                val concatenatedLink = "$baseUrl/$link"
//                concatenatedLinks.add(concatenatedLink)
//            }
//            myMyCoursesDB?.method = JsonUtils.getString("method", myCoursesDoc)
//            myMyCoursesDB?.gradeLevel = JsonUtils.getString("gradeLevel", myCoursesDoc)
//            myMyCoursesDB?.subjectLevel = JsonUtils.getString("subjectLevel", myCoursesDoc)
//            myMyCoursesDB?.createdDate = JsonUtils.getLong("createdDate", myCoursesDoc)
//            myMyCoursesDB?.setNumberOfSteps(JsonUtils.getJsonArray("steps", myCoursesDoc).size())
//            val courseStepsJsonArray = JsonUtils.getJsonArray("steps", myCoursesDoc)
//            val courseStepsList = mutableListOf<RealmCourseStep>()
//
//            for (i in 0 until courseStepsJsonArray.size()) {
//                val stepId = Base64.encodeToString(courseStepsJsonArray[i].toString().toByteArray(), Base64.NO_WRAP)
//                val stepJson = courseStepsJsonArray[i].asJsonObject
//                val step = RealmCourseStep()
//                step.id = stepId
//                step.stepTitle = JsonUtils.getString("stepTitle", stepJson)
//                step.description = JsonUtils.getString("description", stepJson)
//                val stepDescription = JsonUtils.getString("description", stepJson)
//                val stepLinks = extractLinks(stepDescription)
//                for (stepLink in stepLinks) {
//                    val concatenatedLink = "$baseUrl/$stepLink"
//                    concatenatedLinks.add(concatenatedLink)
//                }
//                insertCourseStepsAttachments(myMyCoursesDB?.courseId, stepId, JsonUtils.getJsonArray("resources", stepJson), mRealm)
//                insertExam(stepJson, mRealm, stepId, i + 1, myMyCoursesDB?.courseId)
//                insertSurvey(stepJson, mRealm, stepId, i + 1, myMyCoursesDB?.courseId, myMyCoursesDB?.createdDate)
//                step.noOfResources = JsonUtils.getJsonArray("resources", stepJson).size()
//                step.courseId = myMyCoursesDB?.courseId
//                courseStepsList.add(step)
//            }
//
//            if (mRealm.isInTransaction) {
//                mRealm.commitTransaction()
//            }
//
//            if (!mRealm.isInTransaction) {
//                mRealm.beginTransaction()
//            }
//            myMyCoursesDB?.courseSteps = RealmList()
//            myMyCoursesDB?.courseSteps?.addAll(courseStepsList)
//            mRealm.commitTransaction()
//
//            val csvRow = arrayOf(
//                JsonUtils.getString("_id", myCoursesDoc),
//                JsonUtils.getString("_rev", myCoursesDoc),
//                JsonUtils.getString("languageOfInstruction", myCoursesDoc),
//                JsonUtils.getString("courseTitle", myCoursesDoc),
//                JsonUtils.getInt("memberLimit", myCoursesDoc).toString(),
//                JsonUtils.getString("description", myCoursesDoc),
//                JsonUtils.getString("method", myCoursesDoc),
//                JsonUtils.getString("gradeLevel", myCoursesDoc),
//                JsonUtils.getString("subjectLevel", myCoursesDoc),
//                JsonUtils.getLong("createdDate", myCoursesDoc).toString(),
//                JsonUtils.getJsonArray("steps", myCoursesDoc).toString()
//            )
//            courseDataList.add(csvRow)
//        }
//
//        fun writeCsv(filePath: String, data: List<Array<String>>) {
//            try {
//                val file = File(filePath)
//                file.parentFile?.mkdirs()
//                val writer = CSVWriter(FileWriter(file))
//                writer.writeNext(arrayOf("courseId", "course_rev", "languageOfInstruction", "courseTitle", "memberLimit", "description", "method", "gradeLevel", "subjectLevel", "createdDate", "steps"))
//                for (row in data) {
//                    writer.writeNext(row)
//                }
//                writer.close()
//            } catch (e: IOException) {
//                e.printStackTrace()
//            }
//        }
//
//        fun courseWriteCsv() {
//            writeCsv("${context.getExternalFilesDir(null)}/ole/course.csv", courseDataList)
//        }
//
//        @JvmStatic
//        fun saveConcatenatedLinksToPrefs() {
//            val settings: SharedPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
//            val existingJsonLinks = settings.getString("concatenated_links", null)
//            val existingConcatenatedLinks = if (existingJsonLinks != null) {
//                gson.fromJson(existingJsonLinks, Array<String>::class.java).toMutableList()
//            } else {
//                mutableListOf()
//            }
//            val linksToProcess: List<String>
//            synchronized(concatenatedLinks) {
//                linksToProcess = concatenatedLinks.toList()
//            }
//            for (link in linksToProcess) {
//                if (!existingConcatenatedLinks.contains(link)) {
//                    existingConcatenatedLinks.add(link)
//                }
//            }
//            val jsonConcatenatedLinks = gson.toJson(existingConcatenatedLinks)
//            settings.edit().putString("concatenated_links", jsonConcatenatedLinks).apply()
//        }
//
//        fun getCourseSteps(mRealm: Realm, courseId: String?): List<RealmCourseStep> {
//            val myCourse = mRealm.where<RealmMyCourse>().equalTo("id", courseId).findFirst()
//            val courseSteps = myCourse?.courseSteps ?: emptyList()
//            return courseSteps
//        }
//
//        fun getCourseStepIds(mRealm: Realm, courseId: String?): Array<String?> {
//            val course = mRealm.where<RealmMyCourse>().equalTo("courseId", courseId).findFirst()
//            val stepIds = course?.courseSteps?.map { it.id }?.toTypedArray() ?: emptyArray()
//            return stepIds
//        }
//
//        private fun insertExam(stepContainer: JsonObject, mRealm: Realm, stepId: String, i: Int, myCoursesID: String?) {
//            if (stepContainer.has("exam")) {
//                val `object` = stepContainer.getAsJsonObject("exam")
//                `object`.addProperty("stepNumber", i)
//                insertCourseStepsExams(myCoursesID, stepId, `object`, mRealm)
//            }
//        }
//
//        private fun insertSurvey(stepContainer: JsonObject, mRealm: Realm, stepId: String, i: Int, myCoursesID: String?, createdDate: Long?) {
//            if (stepContainer.has("survey")) {
//                val `object` = stepContainer.getAsJsonObject("survey")
//                `object`.addProperty("stepNumber", i)
//                `object`.addProperty("createdDate", createdDate)
//                insertCourseStepsExams(myCoursesID, stepId, `object`, mRealm)
//            }
//        }
//
//        private fun insertCourseStepsAttachments(myCoursesID: String?, stepId: String?, resources: JsonArray, mRealm: Realm?) {
//            resources.forEach { resource ->
//                if (mRealm != null) {
//                    createStepResource(mRealm, resource.asJsonObject, myCoursesID, stepId)
//                }
//            }
//        }
//
//        @JvmStatic
//        fun getMyByUserId(mRealm: Realm, settings: SharedPreferences?): RealmResults<RealmMyCourse> {
//            val userId = settings?.getString("userId", "--")
//            return mRealm.where(RealmMyCourse::class.java)
//                .equalTo("userId", userId)
//                .findAll()
//        }
//
//        @JvmStatic
//        fun getMyCourseByUserId(userId: String?, libs: List<RealmMyCourse>?): List<RealmMyCourse> {
//            val libraries: MutableList<RealmMyCourse> = ArrayList()
//            for (item in libs ?: emptyList()) {
//                if (item.userId?.contains(userId) == true) {
//                    libraries.add(item)
//                }
//            }
//            return libraries
//        }
//
//        @JvmStatic
//        fun getOurCourse(userId: String?, libs: List<RealmMyCourse>): List<RealmMyCourse> {
//            val libraries: MutableList<RealmMyCourse> = ArrayList()
//            for (item in libs) {
//                if (!item.userId?.contains(userId)!!) {
//                    libraries.add(item)
//                }
//            }
//            return libraries
//        }
//
//        @JvmStatic
//        fun isMyCourse(userId: String?, courseId: String?, realm: Realm): Boolean {
//            return getMyCourseByUserId(userId, realm.where(RealmMyCourse::class.java).equalTo("courseId", courseId).findAll()).isNotEmpty()
//        }
//
//        @JvmStatic
//        fun getCourseByCourseId(courseId: String, mRealm: Realm): RealmMyCourse? {
//            return mRealm.where(RealmMyCourse::class.java).equalTo("courseId", courseId).findFirst()
//        }
//
//        @JvmStatic
//        fun insert(mRealm: Realm, myCoursesDoc: JsonObject?) {
//            insertMyCourses("", myCoursesDoc, mRealm)
//        }
//
//        @JvmStatic
//        fun getMyCourse(mRealm: Realm, id: String?): RealmMyCourse? {
//            return mRealm.where(RealmMyCourse::class.java).equalTo("courseId", id).findFirst()
//        }
//
//        @JvmStatic
//        fun createMyCourse(course: RealmMyCourse?, mRealm: Realm, id: String?) {
//            if (!mRealm.isInTransaction) {
//                mRealm.beginTransaction()
//            }
//            course?.setUserId(id)
//            mRealm.commitTransaction()
//        }
//
//        @JvmStatic
//        fun getMyCourseIds(realm: Realm?, userId: String?): JsonArray {
//            val myCourses = getMyCourseByUserId(userId, realm?.where(RealmMyCourse::class.java)?.findAll())
//            val ids = JsonArray()
//            for (lib in myCourses) {
//                ids.add(lib.courseId)
//            }
//            return ids
//        }
//    }
//}