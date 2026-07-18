package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import javax.inject.Inject
import javax.inject.Singleton
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.dao.legacy.AnswerDao
import org.ole.planet.myplanet.data.room.dao.legacy.ExamDao
import org.ole.planet.myplanet.data.room.dao.legacy.SubmissionDao
import org.ole.planet.myplanet.data.room.entity.legacy.RoomSubmissionEntity
import org.ole.planet.myplanet.data.room.entity.legacy.toRealmModel
import org.ole.planet.myplanet.data.room.entity.legacy.toRoomEntity
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Response

@Singleton
class UploadRepositoryImpl @Inject constructor(
    private val apiInterface: ApiInterface,
    private val examDao: ExamDao,
    private val submissionDao: SubmissionDao,
    private val answerDao: AnswerDao,
) : UploadRepository {

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> queryPending(config: UploadQueryContract<T>): List<T> {
        return when (config.queryType) {
            UploadQueryType.AdoptedSurveys -> examDao.getPendingAdoptedSurveys()
                .map { it.toRealmModel() } as List<T>

            UploadQueryType.ExamResults -> hydrateSubmissions(submissionDao.getPendingExamResults()) as List<T>

            UploadQueryType.CompletedSubmissions -> hydrateSubmissions(submissionDao.getPendingSubmissions()) as List<T>
        }
    }

    override suspend fun markUploaded(
        config: UploadUpdateContract,
        succeeded: List<UploadedItemResult>
    ): List<UploadedItemResult> {
        return when (config.updateType) {
            UploadUpdateType.Exams -> markExamsUploaded(succeeded)
            UploadUpdateType.Submissions -> succeeded.filter { result ->
                submissionDao.markUploaded(result.localId, result.remoteId, result.remoteRev) == 0
            }
        }
    }

    override suspend fun postUpload(
        url: String,
        serializedData: JsonObject
    ): Response<JsonObject> {
        return apiInterface.postDoc(UrlUtils.header, "application/json", url, serializedData)
    }

    override suspend fun putUpload(
        url: String,
        serializedData: JsonObject
    ): Response<JsonObject> {
        return apiInterface.putDoc(UrlUtils.header, "application/json", url, serializedData)
    }

    override suspend fun fetchExistingDoc(url: String): Response<JsonObject> {
        return apiInterface.getJsonObject(UrlUtils.header, url)
    }

    private suspend fun hydrateSubmissions(rows: List<RoomSubmissionEntity>): List<Submission> {
        if (rows.isEmpty()) return emptyList()
        val answersBySubmissionId =
            answerDao.getBySubmissionIds(rows.map { it.id }).groupBy { it.submissionId }
        return rows.map { row -> row.toRealmModel(answersBySubmissionId[row.id].orEmpty()) }
    }

    private suspend fun markExamsUploaded(
        succeeded: List<UploadedItemResult>
    ): List<UploadedItemResult> {
        if (succeeded.isEmpty()) return emptyList()
        val existing = examDao.getByIds(succeeded.map { it.localId }).associateBy { it.id }
        val updated = mutableListOf<StepExam>()
        val failed = mutableListOf<UploadedItemResult>()

        succeeded.forEach { result ->
            val exam = existing[result.localId]?.toRealmModel()
            if (exam == null) {
                failed += result
            } else {
                exam._rev = result.remoteRev
                updated += exam
            }
        }

        if (updated.isNotEmpty()) {
            examDao.upsertAll(updated.mapNotNull { it.toRoomEntity() })
        }

        return failed
    }
}
