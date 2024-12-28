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

        fun insertMyCourses(userId: String?, myCoursesDoc: JsonObject, realm: Realm) {
            CoroutineScope(Dispatchers.IO).launch {
                val ID = JsonUtils.getString("_id", myCoursesDoc)
                realm.writeBlocking {
                    var myMyCourseDB = this.query<RealmMyCourse>("id == $0", ID).first().find()

                    if (myMyCourseDB == null) {
                        myMyCourseDB = RealmMyCourse().apply { this.id = ID }
                        copyToRealm(myMyCourseDB)
                    }

                    myMyCourseDB.apply {
                        addUserId(userId ?: "")
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

        fun getCourseSteps(realm: Realm, courseId: String?): List<RealmCourseStep> {
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

        private suspend fun insertCourseStepsAttachments(courseId: String, stepId: String, resources: JsonArray, realm: Realm) {
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

        fun createMyCourse(course: RealmMyCourse?, realm: Realm, id: String?) {
            realm.writeBlocking {
                course?.addUserId(id ?: "")
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