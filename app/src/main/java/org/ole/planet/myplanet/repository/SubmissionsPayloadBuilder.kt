package org.ole.planet.myplanet.repository

import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ole.planet.myplanet.data.room.dao.ExamDao
import org.ole.planet.myplanet.data.room.dao.QuestionDao
import org.ole.planet.myplanet.model.Answer
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DeviceNameProvider
import org.ole.planet.myplanet.utils.KJson
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin
import org.ole.planet.myplanet.utils.withGsonBridgedNumbers

internal fun Submission.examIdFromParentId(): String? {
    return parentId?.substringBefore("@")
}

internal class SubmissionsPayloadBuilder @Inject constructor(
    private val teamsRepositoryProvider: Provider<TeamsRepository>,
    private val sharedPrefManager: SharedPrefManager,
    private val examDao: ExamDao,
    private val questionDao: QuestionDao,
    private val deviceNameProvider: DeviceNameProvider
) {
    private data class PayloadData(
        val user: UserEntity?,
        val exam: StepExam?,
        val questions: List<ExamQuestion>
    )

    private suspend fun getPayloadData(submission: Submission, user: UserEntity?): PayloadData {
        val examId = submission.examIdFromParentId()
        val exam = examId?.let { examDao.getById(it) }
        val questions = exam?.id?.let { questionDao.getByExamId(it) } ?: emptyList()
        return PayloadData(user, exam, questions)
    }

    suspend fun examUploadPayload(submission: Submission, user: UserEntity?): JsonObject {
        val payloadData = getPayloadData(submission, user)
        val resolvedUser = payloadData.user
        val exam = payloadData.exam
        val team = resolveTeamJson(submission)

        return buildJsonObject {
            if (!submission._id.isNullOrEmpty()) {
                put("_id", submission._id)
            }
            if (!submission._rev.isNullOrEmpty()) {
                put("_rev", submission._rev)
            }
            put("parentId", submission.parentId)
            put("type", submission.type)

            team?.let { put("team", it) }

            put("grade", submission.grade)
            put("startTime", submission.startTime)
            put("lastUpdateTime", submission.lastUpdateTime)
            put("status", submission.status)
            addDocumentOrigin()
            put("deviceName", NetworkUtils.getDeviceName())
            put("customDeviceName", deviceNameProvider.getCustomDeviceName())
            put("sender", submission.sender)
            put("source", sharedPrefManager.getPlanetCode())
            put("parentCode", sharedPrefManager.getParentCode())
            put("answers", Answer.serializeAnswer(submission.answers ?: mutableListOf()).withGsonBridgedNumbers())
            if (exam != null) {
                put("parent", StepExam.serializeExam(exam, payloadData.questions).withGsonBridgedNumbers())
            } else {
                put("parent", KJson.parseObjectOrNull(submission.parent) ?: JsonNull)
            }
            val freshUser = resolvedUser?.serialize()?.withGsonBridgedNumbers()
            when {
                freshUser != null -> put("user", freshUser)
                !submission.user.isNullOrEmpty() -> put("user", KJson.parseOrNull(submission.user) ?: JsonNull)
            }
        }
    }

    private suspend fun resolveTeamJson(submission: Submission): JsonObject? {
        val teamRef = submission.teamObject
        val teamId = teamRef?._id?.takeIf { it.isNotBlank() }
            ?: submission.teamId?.takeIf { it.isNotBlank() }
            ?: return null

        val teamName = teamRef?.name?.takeIf { it.isNotBlank() }
        val teamType = teamRef?.type?.takeIf { it.isNotBlank() }
        val localTeam = if (teamName == null || teamType == null) {
            getTeamByIdOrNull(teamId)
        } else {
            null
        }

        return buildJsonObject {
            put("_id", teamId)
            (teamName ?: localTeam?.name?.takeIf { it.isNotBlank() })?.let { put("name", it) }
            (teamType ?: localTeam?.type?.takeIf { it.isNotBlank() })?.let { put("type", it) }
        }
    }

    internal suspend fun getTeamByIdOrNull(teamId: String) = try {
        teamsRepositoryProvider.get().getTeamById(teamId)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    suspend fun submissionPayload(submission: Submission, source: String, parentCode: String, user: UserEntity?): JsonObject {
        val fields = linkedMapOf<String, JsonElement>()

        try {
            val payloadData = getPayloadData(submission, user)
            val exam = payloadData.exam

            if (!submission._id.isNullOrEmpty()) {
                fields["_id"] = JsonPrimitive(submission._id)
            }
            if (!submission._rev.isNullOrEmpty()) {
                fields["_rev"] = JsonPrimitive(submission._rev)
            }

            fields["parentId"] = JsonPrimitive(submission.parentId ?: "")
            fields["type"] = JsonPrimitive(submission.type ?: "survey")
            resolveTeamJson(submission)?.let { fields["team"] = it }
            fields["grade"] = JsonPrimitive(submission.grade)
            fields["startTime"] = JsonPrimitive(submission.startTime)
            fields["lastUpdateTime"] = JsonPrimitive(submission.lastUpdateTime)
            fields["status"] = JsonPrimitive(submission.status ?: "pending")
            fields.putAll(buildJsonObject { addDocumentOrigin() })
            fields["deviceName"] = JsonPrimitive(NetworkUtils.getDeviceName())
            fields["customDeviceName"] = JsonPrimitive(deviceNameProvider.getCustomDeviceName())
            fields["sender"] = JsonPrimitive(submission.sender)
            fields["source"] = JsonPrimitive(source)
            fields["parentCode"] = JsonPrimitive(parentCode)
            fields["answers"] = Answer.serializeAnswer(submission.answers ?: mutableListOf()).withGsonBridgedNumbers()
            if (exam != null) {
                fields["parent"] = StepExam.serializeExam(exam, payloadData.questions).withGsonBridgedNumbers()
            } else if (!submission.parent.isNullOrEmpty()) {
                fields["parent"] = KJson.parseOrNull(submission.parent) ?: JsonNull
            }

            val userJson = payloadData.user?.serialize()?.withGsonBridgedNumbers()
                ?: submission.user?.takeIf { it.isNotEmpty() }?.let { KJson.asJsonObject(KJson.parseOrNull(it) ?: JsonNull) }
            if (userJson != null) {
                fields["user"] = if (submission.membershipDoc != null) {
                    JsonObject(userJson + ("membershipDoc" to buildJsonObject { put("teamId", submission.membershipDoc?.teamId ?: "") }))
                } else {
                    userJson
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Like the Gson object it replaces, a failure part-way keeps the fields added before it.
        return JsonObject(fields)
    }
}
