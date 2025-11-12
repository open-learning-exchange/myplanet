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
import org.ole.planet.myplanet.ui.survey.SurveyDisplayModel
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.TimeUtils.getFormattedDateWithTime

class SurveyRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    databaseService: DatabaseService
) : RealmRepository(databaseService), SurveyRepository {
    override suspend fun getSurveyDisplayModels(
        isTeam: Boolean,
        teamId: String?,
        userId: String?,
        isTeamShareAllowed: Boolean
    ): List<SurveyDisplayModel> {
        val surveys = when {
            isTeam && isTeamShareAllowed -> getAdoptableTeamSurveys(teamId)
            isTeam -> getTeamOwnedSurveys(teamId)
            else -> getIndividualSurveys()
        }
        val surveyInfos = getSurveyInfos(isTeam, teamId, userId, surveys)
        return surveys.map {
            val surveyInfo = surveyInfos[it.id]
            val questionCount = queryCount(org.ole.planet.myplanet.model.RealmExamQuestion::class.java) {
                equalTo("examId", it.id)
            }
            val teamSubmission = if (isTeam) {
                queryFirst(RealmSubmission::class.java) {
                    equalTo("parentId", it.id)
                    equalTo("membershipDoc.teamId", teamId)
                }
            } else {
                null
            }
            SurveyDisplayModel(
                realmStepExam = it,
                submissionCount = surveyInfo?.submissionCount ?: "",
                lastSubmissionDate = surveyInfo?.lastSubmissionDate ?: "",
                creationDate = surveyInfo?.creationDate ?: "",
                questionCount = questionCount,
                teamSubmissionIsValid = teamSubmission?.isValid
            )
        }
    }

    override suspend fun getTeamOwnedSurveys(teamId: String?): List<RealmStepExam> {
        if (teamId.isNullOrEmpty()) return emptyList()

        val teamSubmissionIds = getTeamSubmissionExamIds(teamId)
        return queryList(RealmStepExam::class.java) {
            equalTo("type", "surveys")

            beginGroup()
            equalTo("teamId", teamId)
            if (teamSubmissionIds.isNotEmpty()) {
                or()
                `in`("id", teamSubmissionIds.toTypedArray())
            }
            endGroup()
        }
    }

    override suspend fun getAdoptableTeamSurveys(teamId: String?): List<RealmStepExam> {
        if (teamId.isNullOrEmpty()) return emptyList()

        val teamSubmissionIds = getTeamSubmissionExamIds(teamId)

        return queryList(RealmStepExam::class.java) {
            equalTo("type", "surveys")

            if (teamSubmissionIds.isNotEmpty()) {
                beginGroup()
                equalTo("isTeamShareAllowed", true)
                and()
                not()
                `in`("id", teamSubmissionIds.toTypedArray())
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
        val surveyInfos = mutableMapOf<String, SurveyInfo>()
        for (survey in surveys) {
            val surveyId = survey.id ?: continue
            val submissionCount = if (isTeam) {
                submissions.count { it.parentId == surveyId && it.membershipDoc?.teamId == teamId }.toString()
            } else {
                submissions.count { it.parentId == surveyId && it.userId == userId }.toString()
            }
            val lastSubmissionDate = if (isTeam) {
                submissions.filter { it.parentId == surveyId && it.membershipDoc?.teamId == teamId }
                    .maxByOrNull { it.startTime }?.startTime?.let { getFormattedDateWithTime(it) } ?: ""
            } else {
                submissions.filter { it.parentId == surveyId && it.userId == userId }
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
