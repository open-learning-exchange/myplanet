package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmStepExam
import javax.inject.Inject

interface ExamRepository {
    suspend fun findStepExamById(id: String): RealmStepExam?
    suspend fun findStepExamByStepId(stepId: String): RealmStepExam?
}

class ExamRepositoryImpl @Inject constructor(
    databaseService: DatabaseService,
) : RealmRepository(databaseService), ExamRepository {
    override suspend fun findStepExamById(id: String): RealmStepExam? {
        return findByField(RealmStepExam::class.java, "id", id)
    }

    override suspend fun findStepExamByStepId(stepId: String): RealmStepExam? {
        return findByField(RealmStepExam::class.java, "stepId", stepId)
    }
}
