package org.ole.planet.myplanet.repository

import io.realm.Realm
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

        return withRealmAsync { realm ->
            val teamSubmissionIds = getTeamSubmissionExamIds(realm, teamId)
            val query = realm.where(RealmStepExam::class.java)
                .equalTo("type", "surveys")

            query.beginGroup()
                .equalTo("teamId", teamId)
            if (teamSubmissionIds.isNotEmpty()) {
                query.or().`in`("id", teamSubmissionIds.toTypedArray())
            }
            query.endGroup()

            val results = query.findAll()
            realm.copyFromRealm(results)
        }
    }

    override suspend fun getAdoptableTeamSurveys(teamId: String?): List<RealmStepExam> {
        if (teamId.isNullOrEmpty()) return emptyList()

        return withRealmAsync { realm ->
            val teamSubmissionIds = getTeamSubmissionExamIds(realm, teamId)
            val query = realm.where(RealmStepExam::class.java)
                .equalTo("type", "surveys")

            if (teamSubmissionIds.isNotEmpty()) {
                query.beginGroup()
                    .equalTo("isTeamShareAllowed", true)
                    .and()
                    .not().`in`("id", teamSubmissionIds.toTypedArray())
                query.endGroup()
            } else {
                query.equalTo("isTeamShareAllowed", true)
            }

            val results = query.findAll()
            realm.copyFromRealm(results)
        }
    }

    override suspend fun getIndividualSurveys(): List<RealmStepExam> {
        return queryList(RealmStepExam::class.java) {
            equalTo("type", "surveys")
            equalTo("isTeamShareAllowed", false)
        }
    }

    private fun getTeamSubmissionExamIds(realm: Realm, teamId: String): Set<String> {
        val submissions = realm.where(RealmSubmission::class.java)
            .isNotNull("membershipDoc")
            .findAll()

        return submissions
            .filter { it.membershipDoc?.teamId == teamId }
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
