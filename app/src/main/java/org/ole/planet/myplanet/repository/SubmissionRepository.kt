package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmSubmission

interface SubmissionRepository {
    fun getPendingSurveys(userId: String?): List<RealmSubmission>
}
