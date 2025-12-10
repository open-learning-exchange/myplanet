package org.ole.planet.myplanet.repository

import org.ole.planet.myplanet.model.RealmStepExam

interface ExamRepository {
    suspend fun findStepExamById(id: String): RealmStepExam?
    suspend fun findStepExamByStepId(stepId: String): RealmStepExam?
}
