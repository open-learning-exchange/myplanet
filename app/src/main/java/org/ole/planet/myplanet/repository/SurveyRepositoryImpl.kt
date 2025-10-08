package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.json.JSONException
import org.json.JSONObject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission

class SurveyRepositoryImpl @Inject constructor(
    databaseService: DatabaseService
) : RealmRepository(databaseService), SurveyRepository {

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
}
