package org.ole.planet.myplanet.model

import com.google.gson.JsonObject
import com.opencsv.CSVWriter
import io.realm.kotlin.Realm
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication
import java.io.File
import java.io.FileWriter
import java.io.IOException

class RealmStepExam : RealmObject {
    @PrimaryKey
    var id: String? = null
    var _rev: String? = null
    var createdDate: Long = 0
    var updatedDate: Long = 0
    var createdBy: String? = null
    var totalMarks: Int = 0
    var name: String? = null
    var type: String? = null
    var stepId: String? = null
    var courseId: String? = null
    var sourcePlanet: String? = null
    var passingPercentage: String? = null
    var noOfQuestions: Int = 0
    var isFromNation: Boolean = false

    companion object {
        val examDataList: MutableList<Array<String>> = mutableListOf()

        suspend fun insertCourseStepsExams(myCoursesID: String?, stepId: String?, exam: JsonObject, realm: Realm, parentId: String? = "") {
            realm.write {
                var myExam = this.query<RealmStepExam>(RealmStepExam::class, "id == $0", exam["_id"]?.asString).first().find()
                if (myExam == null) {
                    myExam = RealmStepExam().apply {
                        id = exam["_id"]?.asString ?: parentId.orEmpty()
                    }
                    copyToRealm(myExam)
                }

                checkIdsAndInsert(myCoursesID, stepId, myExam)
                myExam.type = exam["type"]?.asString ?: "exam"
                myExam.name = exam["name"]?.asString
                myExam.passingPercentage = exam["passingPercentage"]?.asString
                myExam._rev = exam["_rev"]?.asString
                myExam.createdBy = exam["createdBy"]?.asString
                myExam.sourcePlanet = exam["sourcePlanet"]?.asString
                myExam.createdDate = exam["createdDate"]?.asLong ?: 0
                myExam.updatedDate = exam["updatedDate"]?.asLong ?: 0
                myExam.totalMarks = exam["totalMarks"]?.asInt ?: 0
                myExam.noOfQuestions = exam["questions"]?.asJsonArray?.size() ?: 0
                myExam.isFromNation = !parentId.isNullOrEmpty()

                val csvRow = arrayOf(
                    exam["_id"]?.asString.orEmpty(),
                    exam["_rev"]?.asString.orEmpty(),
                    exam["name"]?.asString.orEmpty(),
                    exam["passingPercentage"]?.asString.orEmpty(),
                    exam["type"]?.asString.orEmpty(),
                    exam["createdBy"]?.asString.orEmpty(),
                    exam["sourcePlanet"]?.asString.orEmpty(),
                    exam["createdDate"]?.asString.orEmpty(),
                    exam["updatedDate"]?.asString.orEmpty(),
                    exam["totalMarks"]?.asString.orEmpty(),
                    exam["noOfQuestions"]?.asString.orEmpty(),
                    myExam.isFromNation.toString()
                )
                examDataList.add(csvRow)
            }
        }

        private fun checkIdsAndInsert(myCoursesID: String?, stepId: String?, myExam: RealmStepExam?) {
            if (!myCoursesID.isNullOrEmpty()) {
                myExam?.courseId = myCoursesID
            }
            if (!stepId.isNullOrEmpty()) {
                myExam?.stepId = stepId
            }
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                val writer = CSVWriter(FileWriter(file))
                writer.writeNext(
                    arrayOf(
                        "_id", "_rev", "name", "passingPercentage", "type", "createdBy",
                        "sourcePlanet", "createdDate", "updatedDate", "totalMarks",
                        "noOfQuestions", "isFromNation"
                    )
                )
                for (row in data) {
                    writer.writeNext(row)
                }
                writer.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun stepExamWriteCsv() {
            writeCsv("${MainApplication.context.getExternalFilesDir(null)}/ole/stepExam.csv", examDataList)
        }

        suspend fun getNoOfExam(realm: Realm, courseId: String?): Int {
            return realm.query<RealmStepExam>(RealmStepExam::class, "courseId == $0", courseId)
                .count()
                .find()
                .toInt()
        }

        fun serializeExam(realm: Realm, exam: RealmStepExam): JsonObject {
            val jsonObject = JsonObject()
            jsonObject.addProperty("_id", exam.id)
            jsonObject.addProperty("_rev", exam._rev)
            jsonObject.addProperty("name", exam.name)
            jsonObject.addProperty("passingPercentage", exam.passingPercentage)
            jsonObject.addProperty("type", exam.type)
            jsonObject.addProperty("updatedDate", exam.updatedDate)
            jsonObject.addProperty("createdDate", exam.createdDate)
            jsonObject.addProperty("sourcePlanet", exam.sourcePlanet)
            jsonObject.addProperty("totalMarks", exam.totalMarks)
            jsonObject.addProperty("createdBy", exam.createdBy)

            val questions = realm.query<RealmExamQuestion>(RealmExamQuestion::class, "examId == $0", exam.id).find()
            jsonObject.add("questions", RealmExamQuestion.serializeQuestions(questions))
            return jsonObject
        }

        fun getIds(list: List<RealmStepExam>): Array<String?> {
            return list.map {
                if (it.type?.startsWith("survey") == true) it.id else "${it.id}@${it.courseId}"
            }.toTypedArray()
        }

        suspend fun getSurveyCreationTime(realm: Realm, surveyId: String): Long? {
            val survey = realm.query<RealmStepExam>(RealmStepExam::class, "id == $0", surveyId).first().find()
            return survey?.createdDate
        }
    }
}