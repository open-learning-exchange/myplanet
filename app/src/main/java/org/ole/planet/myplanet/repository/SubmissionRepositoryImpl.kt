package org.ole.planet.myplanet.repository

import javax.inject.Inject
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmSubmission

class SubmissionRepositoryImpl @Inject constructor(
    private val databaseService: DatabaseService,
) : SubmissionRepository {

    override fun getPendingSurveys(userId: String?): List<RealmSubmission> {
        return databaseService.realmInstance.where(RealmSubmission::class.java)
            .equalTo("userId", userId)
            .equalTo("status", "pending")
            .equalTo("type", "survey")
            .findAll()
    }
}
