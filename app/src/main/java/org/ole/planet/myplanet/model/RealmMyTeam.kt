package org.ole.planet.myplanet.model

import android.content.SharedPreferences
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utilities.DownloadUtils.extractLinks
import org.ole.planet.myplanet.utilities.DownloadUtils.openDownloadService
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.UrlUtils.getUrl

open class RealmMyTeam : RealmObject() {
    @PrimaryKey
    var _id: String? = null
    var _rev: String? = null
    var courses: RealmList<String>? = null
    @Index
    var teamId: String? = null
    var name: String? = null
    @Index
    var userId: String? = null
    var description: String? = null
    var requests: String? = null
    var sourcePlanet: String? = null
    var limit = 0
    var createdDate: Long = 0
    var resourceId: String? = null
    var status: String? = null
    var teamType: String? = null
    var teamPlanetCode: String? = null
    var userPlanetCode: String? = null
    var parentCode: String? = null
    @Index
    var docType: String? = null
    var title: String? = null
    var route: String? = null
    var services: String? = null
    var createdBy: String? = null
    var rules: String? = null
    var isLeader = false
    var type: String? = null
    var amount = 0
    var date: Long = 0
    var isPublic = false
    var updated = false
    var beginningBalance = 0
    var sales = 0
    var otherIncome = 0
    var wages = 0
    var otherExpenses = 0
    var startDate: Long = 0
    var endDate: Long = 0
    var updatedDate: Long = 0

    companion object {
        @JvmStatic
        fun populateTeamFields(doc: JsonObject, team: RealmMyTeam, includeCourses: Boolean = false) {
            team.userId = JsonUtils.getString("userId", doc)
            team.teamId = JsonUtils.getString("teamId", doc)
            team._rev = JsonUtils.getString("_rev", doc)
            team.name = JsonUtils.getString("name", doc)
            team.sourcePlanet = JsonUtils.getString("sourcePlanet", doc)
            team.title = JsonUtils.getString("title", doc)
            team.description = JsonUtils.getString("description", doc)
            team.limit = JsonUtils.getInt("limit", doc)
            team.status = JsonUtils.getString("status", doc)
            team.teamPlanetCode = JsonUtils.getString("teamPlanetCode", doc)
            team.createdDate = JsonUtils.getLong("createdDate", doc)
            team.resourceId = JsonUtils.getString("resourceId", doc)
            team.teamType = JsonUtils.getString("teamType", doc)
            team.route = JsonUtils.getString("route", doc)
            team.type = JsonUtils.getString("type", doc)
            team.services = JsonUtils.getString("services", doc)
            team.rules = JsonUtils.getString("rules", doc)
            team.parentCode = JsonUtils.getString("parentCode", doc)
            team.createdBy = JsonUtils.getString("createdBy", doc)
            team.userPlanetCode = JsonUtils.getString("userPlanetCode", doc)
            team.isLeader = JsonUtils.getBoolean("isLeader", doc)
            team.amount = JsonUtils.getInt("amount", doc)
            team.date = JsonUtils.getLong("date", doc)
            team.docType = JsonUtils.getString("docType", doc)
            team.isPublic = JsonUtils.getBoolean("public", doc)
            team.beginningBalance = JsonUtils.getInt("beginningBalance", doc)
            team.sales = JsonUtils.getInt("sales", doc)
            team.otherIncome = JsonUtils.getInt("otherIncome", doc)
            team.wages = JsonUtils.getInt("wages", doc)
            team.otherExpenses = JsonUtils.getInt("otherExpenses", doc)
            team.startDate = JsonUtils.getLong("startDate", doc)
            team.endDate = JsonUtils.getLong("endDate", doc)
            team.updatedDate = JsonUtils.getLong("updatedDate", doc)
            team.updated = JsonUtils.getBoolean("updated", doc)

            val coursesArray = JsonUtils.getJsonArray("courses", doc)
            team.courses = RealmList()
            for (e in coursesArray) {
                val id = e.asJsonObject["_id"].asString
                if (!team.courses!!.contains(id)) {
                    team.courses!!.add(id)
                }
            }
        }

        private fun processDescription(description: String?) {
            val links = extractLinks(description ?: "")
            val baseUrl = getUrl()
            val concatenatedLinks = LinkedHashSet<String>()
            for (link in links) {
                val concatenatedLink = "$baseUrl/$link"
                concatenatedLinks.add(concatenatedLink)
            }
            openDownloadService(context, ArrayList(concatenatedLinks), true)
        }

        @JvmStatic
        fun populateReportFields(doc: JsonObject, team: RealmMyTeam) {
            team.description = JsonUtils.getString("description", doc)
            team.beginningBalance = JsonUtils.getInt("beginningBalance", doc)
            team.sales = JsonUtils.getInt("sales", doc)
            team.otherIncome = JsonUtils.getInt("otherIncome", doc)
            team.wages = JsonUtils.getInt("wages", doc)
            team.otherExpenses = JsonUtils.getInt("otherExpenses", doc)
            team.startDate = JsonUtils.getLong("startDate", doc)
            team.endDate = JsonUtils.getLong("endDate", doc)
            team.updatedDate = JsonUtils.getLong("updatedDate", doc)
            team.updated = JsonUtils.getBoolean("updated", doc)
        }

        @JvmStatic
        fun insertMyTeams(doc: JsonObject, mRealm: Realm) {
            val status = JsonUtils.getString("status", doc)
            if (status == "archived") {
                return
            }

            val teamId = JsonUtils.getString("_id", doc)
            var myTeams = mRealm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
            if (myTeams == null) {
                myTeams = mRealm.createObject(RealmMyTeam::class.java, teamId)
            }
            myTeams?.let {
                populateTeamFields(doc, it, true)
                processDescription(it.description)
            }
        }

        @JvmStatic
        fun getResourceIds(teamId: String?, realm: Realm): MutableList<String> {
            val teams = realm.where(RealmMyTeam::class.java).equalTo("teamId", teamId).findAll()
            val ids = mutableListOf<String>()
            for (team in teams) {
                if (!team.resourceId.isNullOrBlank()) {
                    ids.add(team.resourceId!!)
                }
            }
            return ids
        }

        @JvmStatic
        fun getResourceIdsByUser(userId: String?, realm: Realm): MutableList<String> {
            val list = realm.where(RealmMyTeam::class.java)
                .equalTo("userId", userId)
                .equalTo("docType", "membership")
                .findAll()
            val teamIds = mutableListOf<String>()
            for (team in list) {
                if (!team.teamId.isNullOrBlank()) {
                    teamIds.add(team.teamId!!)
                }
            }
            val l2 = realm.where(RealmMyTeam::class.java)
                .`in`("teamId", teamIds.toTypedArray())
                .equalTo("docType", "resourceLink")
                .findAll()
            val ids = mutableListOf<String>()
            for (team in l2) {
                if (!team.resourceId.isNullOrBlank()) {
                    ids.add(team.resourceId!!)
                }
            }
            return ids
        }

        @JvmStatic
        fun getTeamCreator(teamId: String?, realm: Realm?): String {
            val teams = realm?.where(RealmMyTeam::class.java)?.equalTo("teamId", teamId)?.findFirst()
            return teams?.userId ?: ""
        }

        @JvmStatic
        fun insert(mRealm: Realm, doc: JsonObject) {
            insertMyTeams(doc, mRealm)
        }

        @JvmStatic
        fun getRequestedMember(teamId: String, realm: Realm): MutableList<RealmUserModel> {
            return getUsers(teamId, realm, "request")
        }

        @JvmStatic
        fun getJoinedMember(teamId: String, realm: Realm): MutableList<RealmUserModel> {
            return getUsers(teamId, realm, "membership")
        }

        @JvmStatic
        fun getJoinedMemberCount(teamId: String, realm: Realm): Int {
            return getUsers(teamId, realm, "membership").size
        }

        @JvmStatic
        fun isTeamLeader(teamId: String?, userId: String?, realm: Realm): Boolean {
            val team = realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", teamId)
                .equalTo("docType", "membership")
                .equalTo("userId", userId)
                .equalTo("isLeader", true)
                .findFirst()
            return team != null
        }

        @JvmStatic
        fun getUsers(teamId: String?, mRealm: Realm, docType: String): MutableList<RealmUserModel> {
            var query = mRealm.where(RealmMyTeam::class.java).equalTo("teamId", teamId)
            if (docType.isNotEmpty()) {
                query = query.equalTo("docType", docType)
            }
            val myTeam = query.findAll()
            val list = mutableListOf<RealmUserModel>()
            for (team in myTeam) {
                val model = mRealm.where(RealmUserModel::class.java)
                    .equalTo("id", team.userId)
                    .findFirst()
                if (model != null && !list.contains(model)) list.add(model)
            }
            return list
        }

        @JvmStatic
        fun serialize(team: RealmMyTeam, realm: Realm): JsonObject {
            val `object` = JsonObject()

            JsonUtils.addString(`object`, "_id", team._id)
            JsonUtils.addString(`object`, "_rev", team._rev)
            `object`.addProperty("name", team.name)
            `object`.addProperty("userId", team.userId)
            if (team.docType != "report" && team.docType != "request") {
                `object`.addProperty("limit", team.limit)
                `object`.addProperty("amount", team.amount)
                `object`.addProperty("date", team.date)
                `object`.addProperty("public", team.isPublic)
                `object`.addProperty("isLeader", team.isLeader)
            }
            if (team.docType != "request") {
                `object`.addProperty("createdDate", team.createdDate)
                `object`.addProperty("description", team.description)
                `object`.addProperty("beginningBalance", team.beginningBalance)
                `object`.addProperty("sales", team.sales)
                `object`.addProperty("otherIncome", team.otherIncome)
                `object`.addProperty("wages", team.wages)
                `object`.addProperty("otherExpenses", team.otherExpenses)
                `object`.addProperty("startDate", team.startDate)
                `object`.addProperty("endDate", team.endDate)
                `object`.addProperty("updatedDate", team.updatedDate)
            }
            JsonUtils.addString(`object`, "teamId", team.teamId)
            `object`.addProperty("teamType", team.teamType)
            `object`.addProperty("teamPlanetCode", team.teamPlanetCode)
            `object`.addProperty("docType", team.docType)
            `object`.addProperty("status", team.status)
            `object`.addProperty("userPlanetCode", team.userPlanetCode)
            `object`.addProperty("parentCode", team.parentCode)
            `object`.addProperty("type", team.type)
            `object`.addProperty("route", team.route)
            `object`.addProperty("sourcePlanet", team.sourcePlanet)
            `object`.addProperty("services", team.services)
            `object`.addProperty("createdBy", team.createdBy)
            `object`.addProperty("resourceId", team.resourceId)
            `object`.addProperty("rules", team.rules)

            if (team.teamType == "debit" || team.teamType == "credit") {
                `object`.addProperty("type", team.teamType)
            }

            if (!team.courses.isNullOrEmpty()) {
                val coursesArray = serializeCoursesForTeam(team.courses!!, realm)
                `object`.add("courses", coursesArray)
            }

            return JsonParser.parseString(JsonUtils.gson.toJson(`object`)).asJsonObject
        }

        @JvmStatic
        private fun serializeCoursesForTeam(courseIds: RealmList<String>, realm: Realm): JsonArray {
            val coursesArray = JsonArray()

            for (courseId in courseIds) {
                val course = realm.where(RealmMyCourse::class.java)
                    .equalTo("courseId", courseId)
                    .findFirst()

                course?.let {
                    val courseJson = serializeCourseForTeam(it, realm)
                    coursesArray.add(courseJson)
                }
            }

            return coursesArray
        }

        @JvmStatic
        private fun serializeCourseForTeam(course: RealmMyCourse, realm: Realm): JsonObject {
            val courseJson = JsonObject()

            courseJson.addProperty("_id", course.courseId)
            courseJson.addProperty("_rev", course.courseRev)
            courseJson.addProperty("courseTitle", course.courseTitle)
            courseJson.addProperty("description", course.description)
            courseJson.addProperty("languageOfInstruction", course.languageOfInstruction)
            courseJson.addProperty("gradeLevel", course.gradeLevel)
            courseJson.addProperty("subjectLevel", course.subjectLevel)
            courseJson.addProperty("createdDate", course.createdDate)
            courseJson.addProperty("method", course.method)

            val fullCourse = realm.where(RealmMyCourse::class.java)
                .equalTo("courseId", course.courseId)
                .findFirst()

            fullCourse?.let {
                courseJson.addProperty("creator", "")
                courseJson.addProperty("sourcePlanet", "")
                courseJson.addProperty("resideOn", "")
                courseJson.addProperty("updatedDate", 0)
                courseJson.add("images", JsonArray())

                val stepsArray = serializeStepsForCourse(it.courseSteps, realm, it.courseId)
                courseJson.add("steps", stepsArray)
            }

            return courseJson
        }

        @JvmStatic
        private fun serializeStepsForCourse(steps: RealmList<RealmCourseStep>?, realm: Realm, courseId: String?): JsonArray {
            val stepsArray = JsonArray()

            steps?.forEach { step ->
                val stepJson = JsonObject()
                stepJson.addProperty("stepTitle", step.stepTitle)
                stepJson.addProperty("description", step.description)
                stepJson.addProperty("id", step.id)

                val resourcesArray = serializeStepResources(step.id, courseId, realm)
                stepJson.add("resources", resourcesArray)

                val exam = realm.where(RealmStepExam::class.java)
                    .equalTo("stepId", step.id)
                    .equalTo("courseId", courseId)
                    .findFirst()

                exam?.let {
                    val examJson = JsonObject()
                    examJson.addProperty("name", it.name)
                    examJson.addProperty("passingPercentage", it.passingPercentage)
                    examJson.addProperty("_id", it.id)
                    examJson.addProperty("_rev", it._rev)
                    examJson.addProperty("totalMarks", it.totalMarks)
                    examJson.addProperty("type", it.type)

                    val questions = realm.where(RealmExamQuestion::class.java)
                        .equalTo("examId", it.id)
                        .findAll()
                    examJson.add("questions", RealmExamQuestion.serializeQuestions(questions))

                    if (it.type == "surveys") {
                        stepJson.add("survey", examJson)
                    } else {
                        stepJson.add("exam", examJson)
                    }
                }

                stepsArray.add(stepJson)
            }

            return stepsArray
        }

        @JvmStatic
        private fun serializeStepResources(stepId: String?, courseId: String?, realm: Realm): JsonArray {
            val resourcesArray = JsonArray()

            val resources = realm.where(RealmMyLibrary::class.java)
                .equalTo("stepId", stepId)
                .equalTo("courseId", courseId)
                .findAll()

            resources.forEach { resource ->
                val resourceJson = JsonObject()
                resourceJson.addProperty("_id", resource.resourceId)
                resourceJson.addProperty("_rev", resource._rev)
                resourceJson.addProperty("title", resource.title)
                resourceJson.addProperty("author", resource.author)
                resourceJson.addProperty("year", resource.year)
                resourceJson.addProperty("description", resource.description)
                resourceJson.addProperty("language", resource.language)
                resourceJson.addProperty("publisher", resource.publisher)
                resourceJson.addProperty("linkToLicense", resource.linkToLicense)
                resourceJson.addProperty("medium", resource.medium)
                resourceJson.addProperty("resourceType", resource.resourceType)
                resourceJson.addProperty("addedBy", resource.addedBy)
                resourceJson.addProperty("isDownloadable", resource.resourceOffline)
                resourceJson.addProperty("createdDate", resource.createdDate)
                resourceJson.addProperty("private", false)
                resourceJson.addProperty("filename", resource.filename)
                resourceJson.addProperty("mediaType", resource.mediaType)
                resourceJson.addProperty("openWith", resource.openWith)
                
                val subjectArray = JsonArray()
                resource.subject?.forEach { subject -> subjectArray.add(subject) }
                resourceJson.add("subject", subjectArray)

                val levelArray = JsonArray()
                resource.level?.forEach { level -> levelArray.add(level) }
                resourceJson.add("level", levelArray)

                val resourceForArray = JsonArray()
                resourceForArray.add("default")
                resourceForArray.add("leader")
                resourceForArray.add("learner")
                resourceJson.add("resourceFor", resourceForArray)

                resourcesArray.add(resourceJson)
            }

            return resourcesArray
        }

        fun getMyTeamsByUserId(mRealm: Realm, settings: SharedPreferences?): RealmResults<RealmMyTeam> {
            val userId = settings?.getString("userId", "--") ?: "--"
            val list = mRealm.where(RealmMyTeam::class.java)
                .equalTo("userId", userId)
                .equalTo("docType", "membership")
                .findAll()

            val teamIds = list.map { it.teamId }.toTypedArray()
            return mRealm.where(RealmMyTeam::class.java)
                .`in`("_id", teamIds)
                .notEqualTo("status", "archived")
                .findAll()
        }
    }

    fun requested(userId: String?, mRealm: Realm): Boolean {
        val m = mRealm.where(RealmMyTeam::class.java)
            .equalTo("docType", "request")
            .equalTo("teamId", _id)
            .equalTo("userId", userId)
            .findAll()

        return m.isNotEmpty()
    }

    fun isMyTeam(userID: String?, mRealm: Realm): Boolean {
        return mRealm.where(RealmMyTeam::class.java)
            .equalTo("userId", userID)
            .equalTo("teamId", _id)
            .equalTo("docType", "membership")
            .count() > 0
    }

    fun leave(user: RealmUserModel?, mRealm: Realm) {
        val teams = mRealm.where(RealmMyTeam::class.java)
            .equalTo("userId", user?.id)
            .equalTo("teamId", this._id)
            .equalTo("docType", "membership")
            .findAll()

        for (team in teams) {
            if (team != null) {
                removeTeam(team, mRealm)
            }
        }
    }

    private fun removeTeam(team: RealmMyTeam, mRealm: Realm) {
        if (!mRealm.isInTransaction) mRealm.beginTransaction()
        team.deleteFromRealm()
        mRealm.commitTransaction()
    }
}
