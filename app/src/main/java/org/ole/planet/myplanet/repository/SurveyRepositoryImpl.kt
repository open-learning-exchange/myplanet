package org.ole.planet.myplanet.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.json.JSONException
import org.json.JSONObject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
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

        // Get IDs of original surveys that have been adopted in new format
        // These should be excluded even if they have submissions
        val adoptedSourceSurveyIds = queryList(RealmStepExam::class.java, ensureLatest = true) {
            equalTo("teamId", teamId)
            isNotNull("teamSourceSurveyId")
        }.mapNotNull { it.teamSourceSurveyId }.toSet()

        android.util.Log.d("SurveyRepository", "getTeamOwnedSurveys - teamId: $teamId, teamSubmissionIds: $teamSubmissionIds, adoptedSourceSurveyIds: $adoptedSourceSurveyIds")

        // Filter out submission IDs that are original surveys already adopted
        val filteredSubmissionIds = teamSubmissionIds.filterNot { adoptedSourceSurveyIds.contains(it) }

        android.util.Log.d("SurveyRepository", "getTeamOwnedSurveys - filteredSubmissionIds: $filteredSubmissionIds")

        // Use ensureLatest = true to refresh Realm and see newly adopted surveys
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

        android.util.Log.d("SurveyRepository", "getTeamOwnedSurveys - Found ${result.size} surveys")
        result.forEach {
            android.util.Log.d("SurveyRepository", "  - Survey: id=${it.id}, name=${it.name}, teamSourceSurveyId=${it.teamSourceSurveyId}, _rev=${it._rev}")
        }

        return result
    }

    override suspend fun getAdoptableTeamSurveys(teamId: String?): List<RealmStepExam> {
        if (teamId.isNullOrEmpty()) return emptyList()

        val teamSubmissionIds = getTeamSubmissionExamIds(teamId)

        // Get IDs of surveys already adopted in new format (with teamSourceSurveyId)
        // Use ensureLatest = true to refresh Realm and see newly adopted surveys
        val adoptedSurveyIds = queryList(RealmStepExam::class.java, ensureLatest = true) {
            equalTo("teamId", teamId)
            isNotNull("teamSourceSurveyId")
        }.mapNotNull { it.teamSourceSurveyId }.toSet()

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
        } catch (error: JSONException) {
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
        // Filter out adoption-only submissions (status = "")
        // These are created for backward compatibility but don't represent actual survey responses
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
            val creationDate = survey.createdDate.let { formatDate(it, "MMM dd, yyyy") } ?: ""
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
}
