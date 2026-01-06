package org.ole.planet.myplanet.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.json.JSONException
import org.json.JSONObject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.ui.survey.SurveyFormState
import org.ole.planet.myplanet.ui.survey.SurveyInfo
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import android.content.SharedPreferences
import org.ole.planet.myplanet.model.RealmMembershipDoc
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.TimeUtils.getFormattedDateWithTime
import java.util.UUID

class SurveysRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    databaseService: DatabaseService,
    private val userProfileDbHandler: UserProfileDbHandler,
    private val settings: SharedPreferences,
) : RealmRepository(databaseService), SurveysRepository {

    override suspend fun adoptSurvey(examId: String, userId: String?, teamId: String?, isTeam: Boolean) {
        databaseService.withRealmAsync { realm ->
            realm.executeTransaction { transactionRealm ->
                val exam = transactionRealm.where(RealmStepExam::class.java).equalTo("id", examId).findFirst()
                if (exam == null) {
                    return@executeTransaction
                }

                val userModel = userProfileDbHandler.userModel
                val sParentCode = settings.getString("parentCode", "")
                val planetCode = settings.getString("planetCode", "")

                val parentJsonString = try {
                    JSONObject().apply {
                        put("_id", exam.id)
                        put("name", exam.name)
                        put("courseId", exam.courseId ?: "")
                        put("sourcePlanet", exam.sourcePlanet ?: "")
                        put("teamShareAllowed", exam.isTeamShareAllowed)
                        put("noOfQuestions", exam.noOfQuestions)
                        put("isFromNation", exam.isFromNation)
                    }.toString()
                } catch (e: Exception) {
                    e.printStackTrace()
                    "{}"
                }

                val userJsonString = try {
                    JSONObject().apply {
                        put("doc", JSONObject().apply {
                            put("_id", userModel?.id)
                            put("name", userModel?.name)
                            put("userId", userModel?.id ?: "")
                            put("teamPlanetCode", planetCode ?: "")
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
                } catch (e: Exception) {
                    e.printStackTrace()
                    "{}"
                }

                val teamName = if (isTeam && teamId != null) {
                    transactionRealm.where(RealmMyTeam::class.java)
                        .equalTo("_id", teamId)
                        .findFirst()?.name
                } else null

                if (isTeam && teamId != null && teamName != null) {
                    val newSurveyId = UUID.randomUUID().toString()

                    val existingSurvey = transactionRealm.where(RealmStepExam::class.java)
                        .equalTo("sourceSurveyId", examId)
                        .equalTo("teamId", teamId)
                        .findFirst()

                    if (existingSurvey == null) {
                        transactionRealm.createObject(RealmStepExam::class.java, newSurveyId).apply {
                            this._rev = null
                            this.createdDate = System.currentTimeMillis()
                            this.updatedDate = System.currentTimeMillis()
                            this.adoptionDate = System.currentTimeMillis()
                            this.createdBy = userModel?.id
                            this.totalMarks = exam.totalMarks
                            this.name = "${exam.name} - $teamName"
                            this.description = exam.description
                            this.type = exam.type
                            this.stepId = exam.stepId
                            this.courseId = exam.courseId
                            this.sourcePlanet = exam.sourcePlanet
                            this.passingPercentage = exam.passingPercentage
                            this.noOfQuestions = exam.noOfQuestions
                            this.isFromNation = exam.isFromNation
                            this.teamId = teamId
                            this.sourceSurveyId = examId
                            this.isTeamShareAllowed = false
                        }

                        val questions = transactionRealm.where(RealmExamQuestion::class.java)
                            .equalTo("examId", examId)
                            .findAll()

                        val questionsArray = RealmExamQuestion.serializeQuestions(questions)
                        RealmExamQuestion.insertExamQuestions(questionsArray, newSurveyId, transactionRealm)
                    }
                }

                val adoptionId = "${UUID.randomUUID()}"
                val existingAdoption = if (isTeam && teamId != null) {
                    transactionRealm.where(RealmSubmission::class.java)
                        .equalTo("userId", userId)
                        .equalTo("parentId", examId)
                        .equalTo("status", "")
                        .equalTo("membershipDoc.teamId", teamId)
                        .findFirst()
                } else {
                    transactionRealm.where(RealmSubmission::class.java)
                        .equalTo("userId", userId)
                        .equalTo("parentId", examId)
                        .equalTo("status", "")
                        .isNull("membershipDoc")
                        .findFirst()
                }

                if (existingAdoption == null) {
                    transactionRealm.createObject(RealmSubmission::class.java, adoptionId).apply {
                        this.parentId = examId
                        this.parent = parentJsonString
                        this.userId = userId
                        this.user = userJsonString
                        this.type = "survey"
                        this.status = ""
                        this.uploaded = false
                        this.source = planetCode ?: ""
                        this.parentCode = sParentCode ?: ""
                        this.startTime = System.currentTimeMillis()
                        this.lastUpdateTime = System.currentTimeMillis()
                        this.isUpdated = true

                        if (isTeam && teamId != null) {
                            val team = transactionRealm.where(RealmMyTeam::class.java)
                                .equalTo("_id", teamId)
                                .findFirst()

                            if (team != null) {
                                val teamRef = transactionRealm.createObject(org.ole.planet.myplanet.model.RealmTeamReference::class.java)
                                teamRef._id = team._id
                                teamRef.name = team.name
                                teamRef.type = team.type ?: "team"
                                this.teamObject = teamRef
                            }

                            this.membershipDoc = transactionRealm.createObject(RealmMembershipDoc::class.java).apply {
                                this.teamId = teamId
                            }
                        }
                    }
                }
            }
        }
    }
    override suspend fun getTeamOwnedSurveys(teamId: String?): List<RealmStepExam> {
        if (teamId.isNullOrEmpty()) return emptyList()

        val teamSubmissionIds = getTeamSubmissionExamIds(teamId)
        val adoptedSourceSurveyIds = queryList(RealmStepExam::class.java, ensureLatest = true) {
            equalTo("teamId", teamId)
            isNotNull("sourceSurveyId")
        }.mapNotNull { it.sourceSurveyId }.toSet()

        val filteredSubmissionIds = teamSubmissionIds.filterNot { adoptedSourceSurveyIds.contains(it) }

        val result = queryList(RealmStepExam::class.java, ensureLatest = true) {
            equalTo("type", "surveys")
            beginGroup()
            equalTo("teamId", teamId)
            if (filteredSubmissionIds.isNotEmpty()) {
                or()
                `in`("id", filteredSubmissionIds.toTypedArray())
            }
            endGroup()
        }

        return result
    }

    override suspend fun getAdoptableTeamSurveys(teamId: String?): List<RealmStepExam> {
        if (teamId.isNullOrEmpty()) return emptyList()
        val teamSubmissionIds = getTeamSubmissionExamIds(teamId)
        val adoptedSurveyIds = queryList(RealmStepExam::class.java, ensureLatest = true) {
            equalTo("teamId", teamId)
            isNotNull("sourceSurveyId")
        }.mapNotNull { it.sourceSurveyId }.toSet()

        val allExcludedIds = (teamSubmissionIds + adoptedSurveyIds).toTypedArray()

        return queryList(RealmStepExam::class.java, ensureLatest = true) {
            equalTo("type", "surveys")

            if (allExcludedIds.isNotEmpty()) {
                beginGroup()
                equalTo("isTeamShareAllowed", true)
                and()
                not()
                `in`("id", allExcludedIds)
                endGroup()
            } else {
                equalTo("isTeamShareAllowed", true)
            }
        }
    }

    override suspend fun getIndividualSurveys(): List<RealmStepExam> {
        return queryList(RealmStepExam::class.java) {
            equalTo("type", "surveys")
            equalTo("isTeamShareAllowed", false)
            beginGroup()
            isNull("teamId")
            or()
            equalTo("teamId", "")
            endGroup()
        }
    }

    private suspend fun getTeamSubmissionExamIds(teamId: String): Set<String> {
        val submissions = queryList(RealmSubmission::class.java) {
            isNotNull("membershipDoc")
            equalTo("membershipDoc.teamId", teamId)
        }

        return submissions
            .mapNotNull { parseParentExamId(it.parent) }
            .toSet()
    }

    private fun parseParentExamId(parent: String?): String? {
        if (parent.isNullOrEmpty()) {
            return null
        }
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
        surveys: List<RealmStepExam>
    ): Map<String, SurveyInfo> {
        val surveyIds = surveys.map { it.id }
        val submissionsQuery = queryList(RealmSubmission::class.java, ensureLatest = true) {
            if (isTeam) {
                equalTo("membershipDoc.teamId", teamId)
            } else {
                equalTo("userId", userId)
                isNull("membershipDoc")
            }
        }

        val submissionsByParentId = submissionsQuery
            .filter { submission ->
                val hasStatus = !submission.status.isNullOrEmpty()
                val matchesParentId = surveyIds.any { surveyId ->
                    submission.parentId == surveyId || submission.parentId?.startsWith("$surveyId@") == true
                }
                hasStatus && matchesParentId
            }
            .groupBy { submission ->
                val parentId = submission.parentId ?: return@groupBy null
                surveyIds.find { surveyId ->
                    parentId == surveyId || parentId.startsWith("$surveyId@")
                }
            }
            .filterKeys { it != null }
            .mapKeys { it.key!! }

        return surveys.filter { it.id != null }.associate { survey ->
            val surveyId = survey.id!!
            val surveySubmissions = submissionsByParentId[surveyId] ?: emptyList()
            val submissionCount = surveySubmissions.size
            val lastSubmissionDate = surveySubmissions
                .maxByOrNull { it.startTime }?.startTime?.let { getFormattedDateWithTime(it) } ?: ""
            val creationDate = formatDate(survey.createdDate, "MMM dd, yyyy")

            surveyId to SurveyInfo(
                surveyId = surveyId,
                submissionCount = context.resources.getQuantityString(
                    R.plurals.survey_taken_count,
                    submissionCount,
                    submissionCount
                ),
                lastSubmissionDate = lastSubmissionDate,
                creationDate = creationDate
            )
        }
    }

    override suspend fun getSurveyFormState(
        surveys: List<RealmStepExam>,
        teamId: String?
    ): Map<String, SurveyFormState> {
        val surveyIds = surveys.map { it.id }

        val teamSubmissions = queryList(RealmSubmission::class.java) {
            `in`("parentId", surveyIds.toTypedArray())
            equalTo("membershipDoc.teamId", teamId)
        }.associateBy { it.parentId }

        val questionCounts = queryList(RealmExamQuestion::class.java) {
            `in`("examId", surveyIds.toTypedArray())
        }.groupingBy { it.examId }.eachCount()

        return surveys.filter { it.id != null }.associate { survey ->
            val surveyId = survey.id!!
            val teamSubmission = teamSubmissions[surveyId]
            val questionCount = questionCounts[surveyId] ?: 0
            surveyId to SurveyFormState(teamSubmission, questionCount)
        }
    }

    override suspend fun getSurveySubmissionCount(userId: String?): Int {
        return withRealm { realm ->
            if (userId == null) return@withRealm 0
            realm.where(RealmSubmission::class.java)
                .equalTo("userId", userId)
                .equalTo("type", "survey")
                .equalTo("status", "pending", io.realm.Case.INSENSITIVE)
                .count().toInt()
        }
    }
}
