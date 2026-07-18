package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONException
import org.json.JSONObject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.data.room.dao.ExamDao
import org.ole.planet.myplanet.data.room.dao.QuestionDao
import org.ole.planet.myplanet.data.room.dao.SubmissionDao
import org.ole.planet.myplanet.data.room.dao.TeamDao
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.model.SurveyFormState
import org.ole.planet.myplanet.model.SurveyInfo
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.TimeProvider
import org.ole.planet.myplanet.utils.TimeUtils.formatDate
import org.ole.planet.myplanet.utils.TimeUtils.getFormattedDateWithTime

class SurveysRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val userSessionManager: UserSessionManager,
    private val sharedPrefManager: SharedPrefManager,
    private val dispatcherProvider: DispatcherProvider,
    private val timeProvider: TimeProvider,
    private val examDao: ExamDao,
    private val questionDao: QuestionDao,
    private val submissionDao: SubmissionDao,
    private val teamDao: TeamDao,
) : SurveysRepository {

    private val reminderPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_SURVEY_REMINDERS, Context.MODE_PRIVATE)
    }

    companion object {
        private const val PREF_SURVEY_REMINDERS = "survey_reminders"
        private const val KEY_LAST_SURVEY_DIALOG_SHOWN = "last_survey_dialog_shown"
    }

    override suspend fun getExamQuestions(examId: String): List<ExamQuestion> {
        return questionDao.getByExamId(examId).map { it }
    }

    override suspend fun adoptSurvey(
        examId: String,
        userId: String?,
        teamId: String?,
        isTeam: Boolean
    ) {
        val userModel = userSessionManager.getUserModel()
        val exam = examDao.getById(examId) ?: return

        val sParentCode = sharedPrefManager.getParentCode()
        val planetCode = sharedPrefManager.getPlanetCode()
        val parentJsonString = createParentJsonString(exam)
        val userJsonString = createUserJsonString(userModel, planetCode, isTeam, teamId)

        if (isTeam && !teamId.isNullOrEmpty()) {
            val teamName = teamDao.getById(teamId)?.name ?: teamDao.getByTeamId(teamId)?.name
            if (!teamName.isNullOrEmpty()) {
                val existingSurvey = examDao.getByTeamIdAndType(teamId, "surveys")
                    .firstOrNull { it.sourceSurveyId == examId }
                if (existingSurvey == null) {
                    val newSurveyId = UUID.randomUUID().toString()
                    val mappedSurvey = createMappedSurvey(newSurveyId, examId, exam, userModel, teamName, teamId)
                    examDao.upsert(mappedSurvey ?: return)

                    val questionEntities = ExamQuestion.insertExamQuestions(
                        ExamQuestion.serializeQuestions(getExamQuestions(examId)),
                        newSurveyId
                    ).mapNotNull { it }
                    if (questionEntities.isNotEmpty()) {
                        questionDao.upsertAll(questionEntities)
                    }
                }
            }
        }

        val existingAdoption = findExistingAdoption(userId, examId, teamId, isTeam)
        if (existingAdoption == null) {
            createMappedSubmission(
                adoptionId = UUID.randomUUID().toString(),
                examId = examId,
                parentJsonString = parentJsonString,
                userId = userId,
                userJsonString = userJsonString,
                planetCode = planetCode,
                sParentCode = sParentCode,
                isTeam = isTeam,
                teamId = teamId
            )?.let { submissionDao.upsertAll(listOf(it)) }
        }
    }

    private fun createParentJsonString(exam: StepExam): String {
        return try {
            JSONObject().apply {
                put("_id", exam.id)
                put("name", exam.name)
                put("courseId", exam.courseId ?: "")
                put("sourcePlanet", exam.sourcePlanet ?: "")
                put("teamShareAllowed", exam.isTeamShareAllowed)
                put("noOfQuestions", exam.noOfQuestions)
                put("isFromNation", exam.isFromNation)
            }.toString()
        } catch (_: Exception) {
            "{}"
        }
    }

    private fun createUserJsonString(
        userModel: UserEntity?,
        planetCode: String,
        isTeam: Boolean,
        teamId: String?
    ): String {
        return try {
            JSONObject().apply {
                put("doc", JSONObject().apply {
                    put("_id", userModel?.id)
                    put("name", userModel?.name)
                    put("userId", userModel?.id ?: "")
                    put("teamPlanetCode", planetCode)
                    put("status", "active")
                    put("type", "team")
                    put("createdBy", userModel?.id ?: "")
                })

                if (isTeam && teamId != null) {
                    put("membershipDoc", JSONObject().apply {
                        put("teamId", teamId)
                    })
                }
            }.toString()
        } catch (_: Exception) {
            "{}"
        }
    }

    private fun createMappedSurvey(
        newSurveyId: String,
        examId: String,
        exam: StepExam,
        userModel: UserEntity?,
        teamName: String,
        teamId: String
    ): StepExam {
        return StepExam().apply {
            id = newSurveyId
            _rev = null
            createdDate = timeProvider.now()
            updatedDate = timeProvider.now()
            adoptionDate = timeProvider.now()
            createdBy = userModel?.id
            totalMarks = exam.totalMarks
            name = "${exam.name} - $teamName"
            description = exam.description
            type = exam.type
            stepId = exam.stepId
            courseId = exam.courseId
            sourcePlanet = exam.sourcePlanet
            passingPercentage = exam.passingPercentage
            noOfQuestions = exam.noOfQuestions
            isFromNation = exam.isFromNation
            this.teamId = teamId
            sourceSurveyId = examId
            isTeamShareAllowed = false
        }
    }

    private suspend fun findExistingAdoption(
        userId: String?,
        examId: String,
        teamId: String?,
        isTeam: Boolean
    ): Submission? {
        if (userId.isNullOrEmpty()) return null
        val candidates = if (isTeam && !teamId.isNullOrEmpty()) {
            submissionDao.getByUserIdAndTeamId(userId, teamId)
        } else {
            submissionDao.getByUserIdWithoutTeam(userId)
        }
        return candidates.firstOrNull {
            it.parentId == examId && it.status.orEmpty().isEmpty()
        }
    }

    private suspend fun createMappedSubmission(
        adoptionId: String,
        examId: String,
        parentJsonString: String,
        userId: String?,
        userJsonString: String,
        planetCode: String,
        sParentCode: String,
        isTeam: Boolean,
        teamId: String?
    ): Submission? {
        val submission = Submission().apply {
            id = adoptionId
            parentId = examId
            parent = parentJsonString
            this.userId = userId
            user = userJsonString
            type = "survey"
            status = ""
            uploaded = false
            source = planetCode
            parentCode = sParentCode
            startTime = timeProvider.now()
            lastUpdateTime = timeProvider.now()
            isUpdated = true
            if (isTeam && teamId != null) {
                membershipDoc = org.ole.planet.myplanet.model.MembershipDoc().apply {
                    this.teamId = teamId
                }
            }
        }
        return submission
    }

    override suspend fun getTeamOwnedSurveys(teamId: String?): List<StepExam> {
        if (teamId.isNullOrEmpty()) return emptyList()

        val teamSubmissionIds = getTeamSubmissionExamIds(teamId)
        val adoptedSourceSurveyIds = examDao.getByTeamIdAndType(teamId, "surveys")
            .mapNotNull { it.sourceSurveyId }
            .toSet()
        val filteredSubmissionIds = teamSubmissionIds - adoptedSourceSurveyIds

        return examDao.getByType("surveys")
            .asSequence()
            .filter { it.teamId == teamId || filteredSubmissionIds.contains(it.id) }
            .map { it }
            .toList()
    }

    override suspend fun getAdoptableTeamSurveys(teamId: String?): List<StepExam> {
        if (teamId.isNullOrEmpty()) return emptyList()
        val excludedIds = getTeamSubmissionExamIds(teamId) +
            examDao.getByTeamIdAndType(teamId, "surveys").mapNotNull { it.sourceSurveyId }

        return examDao.getByType("surveys")
            .asSequence()
            .filter { it.isTeamShareAllowed }
            .filterNot { excludedIds.contains(it.id) }
            .map { it }
            .toList()
    }

    override suspend fun getIndividualSurveys(): List<StepExam> {
        return examDao.getByType("surveys")
            .filter { !it.isTeamShareAllowed && it.teamId.isNullOrEmpty() }
            .map { it }
    }

    private suspend fun getTeamSubmissionExamIds(teamId: String): Set<String> {
        return submissionDao.getByTeamId(teamId)
            .mapNotNull { parseParentExamId(it.parent) }
            .toSet()
    }

    private fun parseParentExamId(parent: String?): String? {
        if (parent.isNullOrEmpty()) return null
        return try {
            JSONObject(parent).optString("_id").takeIf { it.isNotEmpty() }
        } catch (_: JSONException) {
            null
        }
    }

    override suspend fun getSurveyInfos(
        isTeam: Boolean,
        teamId: String?,
        userId: String?,
        surveys: List<StepExam>
    ): Map<String, SurveyInfo> {
        val surveyIds = surveys.mapNotNull { it.id }
        val submissions = if (isTeam && !teamId.isNullOrEmpty()) {
            submissionDao.getByTeamId(teamId)
        } else if (!userId.isNullOrEmpty()) {
            submissionDao.getByUserIdWithoutTeam(userId)
        } else {
            emptyList()
        }

        val submissionsByParentId = submissions.filter { submission ->
            val isComplete = submission.status == "complete" || submission.status == "requires grading"
            val matchesParentId = surveyIds.any { surveyId ->
                submission.parentId == surveyId || submission.parentId?.startsWith("$surveyId@") == true
            }
            isComplete && matchesParentId
        }.groupBy { submission ->
            val parentId = submission.parentId ?: return@groupBy null
            surveyIds.find { surveyId -> parentId == surveyId || parentId.startsWith("$surveyId@") }
        }.filterKeys { it != null }

        return surveys.mapNotNull { survey ->
            val surveyId = survey.id
            val surveySubmissions = submissionsByParentId[surveyId].orEmpty()
            val submissionCount = surveySubmissions.size
            surveyId to SurveyInfo(
                surveyId = surveyId,
                submissionCount = context.resources.getQuantityString(
                    R.plurals.survey_taken_count,
                    submissionCount,
                    submissionCount
                ),
                lastSubmissionDate = surveySubmissions.maxByOrNull { it.startTime }
                    ?.startTime
                    ?.let { getFormattedDateWithTime(it) }
                    .orEmpty(),
                creationDate = formatDate(survey.createdDate, "MMM dd, yyyy")
            )
        }.toMap()
    }

    override suspend fun getSurveyFormState(
        surveys: List<StepExam>,
        teamId: String?
    ): Map<String, SurveyFormState> {
        val surveyIds = surveys.mapNotNull { it.id }
        if (surveyIds.isEmpty()) return emptyMap()

        val teamSubmissions = if (teamId.isNullOrEmpty()) {
            emptyMap()
        } else {
            submissionDao.getByParentIdsAndTeamId(surveyIds, teamId)
                .associateBy { it.parentId }
        }
        val questionCounts = questionDao.getByExamIds(surveyIds).groupingBy { it.examId }.eachCount()

        return surveys.mapNotNull { survey ->
            val surveyId = survey.id
            surveyId to SurveyFormState(
                teamSubmissions[surveyId],
                questionCounts[surveyId] ?: 0
            )
        }.toMap()
    }

    override suspend fun getSurveySubmissionCount(userId: String?): Int {
        if (userId.isNullOrEmpty()) return 0
        return submissionDao.getPendingSurveys(userId).size
    }

    override suspend fun getSurvey(id: String): StepExam? {
        return examDao.getById(id)
            ?: examDao.getByType("surveys").firstOrNull { it.name == id }
    }

    override suspend fun getSurveys(): List<StepExam> {
        return examDao.getByType("surveys").map { it }
    }

    override suspend fun getSurveys(ascending: Boolean): List<StepExam> {
        val entities = examDao.getByType("surveys").sortedBy { it.createdDate }
        return (if (ascending) entities else entities.asReversed()).map { it }
    }

    override suspend fun bulkInsertExamsFromSync(jsonArray: JsonArray) {
        val exams = mutableListOf<StepExam>()
        val questions = mutableListOf<ExamQuestion>()

        for (row in jsonArray) {
            var jsonDoc = row.asJsonObject
            jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc)
            val id = JsonUtils.getString("_id", jsonDoc)
            if (id.startsWith("_design")) continue

            val exam = StepExam.insertCourseStepsExams("", "", jsonDoc, "")
            exams += exam
            questions += ExamQuestion.insertExamQuestions(
                JsonUtils.getJsonArray("questions", jsonDoc),
                exam.id
            )
        }

        if (exams.isNotEmpty()) {
            examDao.upsertAll(exams.mapNotNull { it })
        }
        if (questions.isNotEmpty()) {
            questionDao.upsertAll(questions.mapNotNull { it })
        }
    }

    override fun dueRemindersFlow(): Flow<List<String>> = flow {
        while (true) {
            val currentTime = timeProvider.now()
            val toShow = mutableListOf<String>()
            val toRemove = mutableListOf<String>()

            for (entry in reminderPrefs.all) {
                if (entry.key.startsWith("reminder_time_")) {
                    val surveyIds = entry.key.removePrefix("reminder_time_")
                    val reminderTime = reminderPrefs.getLong(entry.key, 0)
                    if (reminderTime <= currentTime) {
                        toShow.add(surveyIds)
                        toRemove.add(surveyIds)
                    }
                }
            }

            if (toShow.isNotEmpty()) {
                emit(toShow)
                reminderPrefs.edit {
                    for (surveyIds in toRemove) {
                        remove("reminder_time_$surveyIds")
                        remove("reminder_surveys_$surveyIds")
                    }
                }
            }
            delay(60_000)
        }
    }.flowOn(dispatcherProvider.io)

    override suspend fun scheduleSurveyReminder(
        surveyIds: String,
        timeUnit: TimeUnit,
        value: Int
    ) {
        val reminderTime = timeProvider.now() + timeUnit.toMillis(value.toLong())
        reminderPrefs.edit {
            putLong("reminder_time_$surveyIds", reminderTime)
                .putString("reminder_surveys_$surveyIds", surveyIds)
        }
    }

    override suspend fun setLastSurveyDialogShown(time: Long) {
        reminderPrefs.edit {
            putLong(KEY_LAST_SURVEY_DIALOG_SHOWN, time)
        }
    }

    override suspend fun getLastSurveyDialogShown(): Long {
        return reminderPrefs.getLong(KEY_LAST_SURVEY_DIALOG_SHOWN, 0L)
    }

    override suspend fun isReminderScheduled(surveyIds: String): Boolean {
        return reminderPrefs.contains("reminder_time_$surveyIds")
    }

    override suspend fun getPendingAdoptedSurveys(): List<StepExam> {
        return examDao.getPendingAdoptedSurveys().map { it }
    }
}
