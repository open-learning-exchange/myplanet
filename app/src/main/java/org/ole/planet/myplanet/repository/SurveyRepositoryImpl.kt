package org.ole.planet.myplanet.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.json.JSONException
import org.json.JSONObject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.SurveyBindingData
import org.ole.planet.myplanet.ui.survey.SurveyInfo
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.TimeUtils.getFormattedDateWithTime

class SurveyRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    databaseService: DatabaseService
) : RealmRepository(databaseService), SurveyRepository {

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
        val submissions = queryList(RealmSubmission::class.java) {
            `in`("parentId", surveyIds.toTypedArray())
        }
        val actualSubmissions = submissions.filter { !it.status.isNullOrEmpty() }

        val surveyInfos = mutableMapOf<String, SurveyInfo>()
        for (survey in surveys) {
            val surveyId = survey.id ?: continue
            val submissionCount = if (isTeam) {
                actualSubmissions.count { it.parentId == surveyId && it.membershipDoc?.teamId == teamId }.toString()
            } else {
                actualSubmissions.count { it.parentId == surveyId && it.userId == userId }.toString()
            }
            val lastSubmissionDate = if (isTeam) {
                actualSubmissions.filter { it.parentId == surveyId && it.membershipDoc?.teamId == teamId }
                    .maxByOrNull { it.startTime }?.startTime?.let { getFormattedDateWithTime(it) } ?: ""
            } else {
                actualSubmissions.filter { it.parentId == surveyId && it.userId == userId }
                    .maxByOrNull { it.startTime }?.startTime?.let { getFormattedDateWithTime(it) } ?: ""
            }
            val creationDate = formatDate(survey.createdDate, "MMM dd, yyyy")
            surveyInfos[surveyId] = SurveyInfo(
                surveyId = surveyId,
                submissionCount = context.resources.getQuantityString(
                    R.plurals.survey_taken_count,
                    submissionCount.toInt(),
                    submissionCount.toInt()
                ),
                lastSubmissionDate = lastSubmissionDate,
                creationDate = creationDate
            )
        }
        return surveyInfos
    }

    override suspend fun getSurveyBindingData(
        surveys: List<RealmStepExam>,
        teamId: String?
    ): Map<String, SurveyBindingData> {
        val surveyIds = surveys.map { it.id }

        val teamSubmissions = queryList(RealmSubmission::class.java) {
            `in`("parentId", surveyIds.toTypedArray())
            equalTo("membershipDoc.teamId", teamId)
        }.associateBy { it.parentId }

        val questions = queryList(RealmExamQuestion::class.java) {
            `in`("examId", surveyIds.toTypedArray())
        }
        val questionCounts = questions.groupingBy { it.examId }.eachCount()

        val bindingData = mutableMapOf<String, SurveyBindingData>()
        for (survey in surveys) {
            val surveyId = survey.id ?: continue
            val teamSubmission = teamSubmissions[surveyId]
            val questionCount = questionCounts[surveyId] ?: 0
            bindingData[surveyId] = SurveyBindingData(teamSubmission, questionCount)
        }
        return bindingData
    }
}
